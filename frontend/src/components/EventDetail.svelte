<script>
  import { createEventDispatcher, onMount } from 'svelte';
  import { api } from '../lib/api.js';
  import { fmt, duration, minutes, toLocalInput, localInputToIso } from '../lib/time.js';
  import { showToast, REASONS, reasonLabel } from '../lib/ui.js';
  import Timeline from './Timeline.svelte';

  export let eventId;
  const dispatch = createEventDispatcher();

  let ev = null;
  let continuity = null;
  let calcs = [];
  let batchTab = 0;
  let editSegments = []; // 编辑中的分段(本地)
  let editMode = false;
  let confirmedBy = '班长';
  let reviewer = '班长';
  let reviewComment = '';
  let conservation = null;
  let resolveTime = '';

  async function load() {
    ev = await api.event(eventId);
    continuity = await api.continuity(eventId);
    try { calcs = await api.calculations(eventId); } catch { calcs = []; }
    if (calcs.length) batchTab = calcs.length - 1;
    if (!editMode) syncEditorFromEvent();
    if (ev.open && !resolveTime) resolveTime = toLocalInput(new Date().toISOString()).slice(0, 19);
    refreshConservation();
  }

  function syncEditorFromEvent() {
    editSegments = ev.segments.length
      ? ev.segments.map((s) => ({
          reason: s.reason,
          startTime: toLocalInput(s.startTime),
          endTime: toLocalInput(s.endTime),
          note: s.note || ''
        }))
      : [{ reason: 'FAILURE', startTime: toLocalInput(ev.startTime), endTime: ev.endTime ? toLocalInput(ev.endTime) : '', note: '' }];
    confirmedBy = ev.segmentedBy || '班长';
  }

  async function refreshConservation() {
    // 只对已保存分段做后端校验
    try { conservation = await api.conservation(eventId); } catch { conservation = null; }
  }

  // 编辑态本地守恒预览(秒级)
  $: localCheck = computeLocalCheck(editSegments, ev, editMode);

  function segDuration(seg) {
    const a = Date.parse((seg.startTime || '').replace(' ', 'T')) / 1000;
    const b = Date.parse((seg.endTime || '').replace(' ', 'T')) / 1000;
    if (!(b > a)) return '时间无效';
    const sec = b - a;
    const m = Math.floor(sec / 60), s = sec % 60;
    return `本段 ${m}分${s ? s + '秒' : ''}`;
  }

  function computeLocalCheck(segs, event, editing) {
    if (!event || !editing) return null;
    const problems = [];
    const sorted = [...segs].sort((a, b) => (a.startTime < b.startTime ? -1 : 1));
    const es = Date.parse((event.startTime || '').toString()) / 1000;
    const ee = event.endTime ? Date.parse(event.endTime) / 1000 : null;
    let covered = 0;
    sorted.forEach((s, i) => {
      const s0 = Date.parse((s.startTime || '').replace(' ', 'T')) / 1000;
      const s1 = Date.parse((s.endTime || '').replace(' ', 'T')) / 1000;
      if (!(s1 > s0)) { problems.push(`第 ${i + 1} 段结束必须晚于开始`); return; }
      covered += s1 - s0;
      if (i === 0 && s0 !== es) problems.push(`开头留白:第 1 段起点须等于停机开始`);
      if (i > 0) {
        const prev = Date.parse((sorted[i - 1].endTime || '').replace(' ', 'T')) / 1000;
        if (prev < s0) problems.push(`第 ${i}/${i + 1} 段之间留白 ${s0 - prev} 秒`);
        if (prev > s0) problems.push(`第 ${i}/${i + 1} 段重叠 ${prev - s0} 秒`);
      }
    });
    if (ee != null && sorted.length) {
      const last = Date.parse((sorted[sorted.length - 1].endTime || '').replace(' ', 'T')) / 1000;
      if (last !== ee) problems.push('结尾留白:最后一段终点须等于恢复时间');
      if (covered !== ee - es) problems.push(`分段合计 ${covered}s ≠ 停机时长 ${ee - es}s`);
    }
    return { problems, covered };
  }

  function startEdit() {
    syncEditorFromEvent();
    editMode = true;
  }

  function addSegment() {
    const last = editSegments[editSegments.length - 1];
    editSegments = [...editSegments, { reason: 'WAIT_MATERIAL', startTime: last?.endTime || '', endTime: ev.endTime ? toLocalInput(ev.endTime) : '', note: '' }];
  }

  function removeSegment(i) {
    editSegments = editSegments.filter((_, idx) => idx !== i);
  }

  async function saveSegments() {
    try {
      await api.saveSegments(eventId, {
        confirmedBy,
        segments: editSegments.map((s) => ({
          reason: s.reason,
          startTime: localInputToIso(s.startTime),
          endTime: localInputToIso(s.endTime),
          note: s.note
        }))
      });
      showToast('分段已保存(守恒校验通过),等待班长复核');
      editMode = false;
      await load();
    } catch (e) {
      showToast(e.message, true);
    }
  }

  async function doResolve() {
    try {
      await api.resolve(eventId, { endTime: localInputToIso(resolveTime) });
      showToast('已登记恢复时间,事件沿用同一单号');
      await load();
    } catch (e) { showToast(e.message, true); }
  }

  async function review(pass) {
    try {
      if (pass) {
        await api.review(eventId, { reviewedBy: reviewer, comment: reviewComment });
        showToast('复核通过:已按当班标准完成首算,进入正式统计');
      } else {
        await api.reject(eventId, { reviewedBy: reviewer, comment: reviewComment || '请重新核对分段' });
        showToast('已驳回分段');
      }
      await load();
    } catch (e) { showToast(e.message, true); }
  }

  async function recalc() {
    try {
      await api.recalc(eventId, { mode: 'CURRENT', calculatedBy: reviewer || '计划员' });
      showToast('已按当前最新标准重新计算;首算批次保留可查');
      await load();
      batchTab = calcs.length - 1;
    } catch (e) { showToast(e.message, true); }
  }

  onMount(load);
</script>

{#if ev}
<div class="panel">
  <div class="row-actions" style="justify-content:space-between;">
    <h2 style="margin:0;">
      <button class="btn small ghost" on:click={() => dispatch('back')}>← 返回</button>
      停机事件 <span class="mono">{ev.eventCode}</span>
      <span class="badge {ev.status.toLowerCase()}">{ev.statusLabel}</span>
      {#if ev.open}<span class="badge open">未恢复</span>{/if}
    </h2>
    <div class="muted">{ev.equipmentCode} · {ev.equipmentName}</div>
  </div>

  <div class="grid cols-3" style="margin-top:12px;">
    <div class="stat"><div class="k">停机开始</div><div class="v" style="font-size:15px;">{fmt(ev.startTime)}</div></div>
    <div class="stat"><div class="k">恢复时间</div><div class="v" style="font-size:15px;">{ev.open ? '未恢复(跨班沿用)' : fmt(ev.endTime)}</div></div>
    <div class="stat"><div class="k">整次停机时长</div><div class="v">{duration(ev.totalDurationSeconds)}</div></div>
  </div>

  {#if continuity}
    <div class="note-box {continuity.crossesShifts ? 'warn' : 'info'}">
      <strong>跨班连续性:</strong>{continuity.note}
      <div style="margin-top:4px;">途经班次:{continuity.shiftLabels.join(' → ')}</div>
    </div>
  {/if}

  {#if ev.open}
    <div class="note-box danger">
      <strong>设备尚未恢复。</strong>跨班未恢复时沿用事件 {ev.eventCode},不能拆成两次独立故障。请登记恢复时间后再分段:
      <div class="row-actions" style="margin-top:8px;">
        <input type="datetime-local" step="1" bind:value={resolveTime} style="max-width:260px;" />
        <button class="btn" on:click={doResolve}>登记恢复(同一事件)</button>
      </div>
    </div>
  {/if}
</div>

<!-- ============ 时间轴分段 ============ -->
<div class="panel">
  <div class="row-actions" style="justify-content:space-between;">
    <h2 style="margin:0;">时间轴分段(班长确认原因)</h2>
    {#if !ev.open && !editMode}
      <button class="btn" on:click={startEdit}>{ev.segments.length ? '修改分段' : '开始分段'}</button>
    {/if}
  </div>

  <div class="legend">
    <span><i style="background:var(--failure);"></i>故障</span>
    <span><i style="background:var(--wait);"></i>待料</span>
    <span><i style="background:var(--change);"></i>换线</span>
    <span class="muted">各段首尾相接,不重叠、不留白,合计 = 整次停机时长</span>
  </div>

  {#if ev.segments.length && !editMode}
    <Timeline {ev} />
  {/if}

  {#if editMode}
    <div class="note-box info">
      停机区间:<span class="mono">{fmt(ev.startTime)}</span> 至 <span class="mono">{fmt(ev.endTime)}</span>
      ,共 {duration(ev.totalDurationSeconds)}
    </div>

    {#each editSegments as seg, i}
      <div class="seg-edit-card">
        <div class="head">
          <strong>第 {i + 1} 段</strong>
          <select bind:value={seg.reason} style="max-width:130px;">
            {#each REASONS as r}<option value={r.key}>{r.label}</option>{/each}
          </select>
          {#if editSegments.length > 1}
            <button class="btn small ghost" on:click={() => removeSegment(i)}>删除</button>
          {/if}
          {#if localCheck}
            <span class="dur muted" style="font-size:12px;">
              {segDuration(seg)}
            </span>
          {/if}
        </div>
        <div class="grid cols-3">
          <label class="field"><span>开始</span><input type="datetime-local" step="1" bind:value={seg.startTime} /></label>
          <label class="field"><span>结束</span><input type="datetime-local" step="1" bind:value={seg.endTime} /></label>
          <label class="field"><span>说明</span><input bind:value={seg.note} placeholder="如:加热圈故障,等维修" /></label>
        </div>
      </div>
    {/each}

    <div class="row-actions">
      <button class="btn ghost" on:click={addSegment}>+ 增加分段</button>
      <button class="btn ghost" on:click={() => { editMode = false; syncEditorFromEvent(); }}>取消</button>
      <button class="btn" on:click={saveSegments} disabled={localCheck && localCheck.problems.length}>
        保存分段(强制守恒校验)
      </button>
    </div>

    {#if localCheck && localCheck.problems.length}
      <div class="note-box danger" style="margin-top:12px;">
        <strong>时间轴未守恒({localCheck.problems.length} 项):</strong>
        <ul>{#each localCheck.problems as p}<li>{p}</li>{/each}</ul>
      </div>
    {:else if localCheck}
      <div class="note-box info" style="margin-top:12px;">
        ✓ 分段合计 {duration(localCheck.covered)},与整次停机时长一致,无重叠无留白
      </div>
    {/if}
  {/if}

  {#if !editMode && ev.segments.length}
    <table>
      <thead><tr><th>#</th><th>原因</th><th>开始</th><th>结束</th><th>时长</th><th>归属班次</th><th>说明</th></tr></thead>
      <tbody>
        {#each ev.segments as s}
          <tr>
            <td>{s.ordinal}</td>
            <td><span class="tag {s.reason}">{s.reasonLabel}</span></td>
            <td class="nowrap mono">{fmt(s.startTime)}</td>
            <td class="nowrap mono">{fmt(s.endTime)}</td>
            <td class="nowrap">{duration(s.durationSeconds)} <span class="muted">({minutes(s.durationSeconds)}分)</span></td>
            <td>{s.shiftLabel}</td>
            <td class="muted">{s.note || '—'}</td>
          </tr>
        {/each}
      </tbody>
    </table>

    <div class="row-actions" style="margin-top:10px;justify-content:space-between;">
      <div>
        分段合计:<strong>{duration(ev.coveredSeconds)}</strong>
        {#if ev.conservationValid}
          <span style="color:var(--ok);font-weight:600;"> ✓ 等于整次停机 {duration(ev.totalDurationSeconds)}</span>
        {:else}
          <span style="color:var(--failure);font-weight:600;"> ✗ 不等于整次停机 {duration(ev.totalDurationSeconds)}</span>
        {/if}
        <span class="muted"> · 分段确认人:{ev.segmentedBy || '—'}</span>
      </div>
    </div>
  {/if}
</div>

<!-- ============ 复核门控 ============ -->
{#if ev.status !== 'REVIEWED'}
<div class="panel">
  <h2>班长复核</h2>
  {#if !ev.segments.length}
    <div class="muted">请先在上方完成时间轴分段并通过守恒校验,才能复核。</div>
  {:else if !ev.conservationValid}
    <div class="note-box danger"><strong>守恒校验未通过,禁止复核。</strong>请修改分段使各段首尾相接、合计等于整次停机时长。</div>
  {:else}
    <div class="note-box info">
      复核通过后将<b>按当班当时生效的节拍标准</b>自动首算损失,事件进入正式统计;
      未复核事件不会出现在任何正式报表中。
    </div>
    <div class="grid cols-2">
      <label class="field"><span>复核人</span><input bind:value={reviewer} placeholder="班长姓名" /></label>
      <label class="field"><span>复核意见</span><input bind:value={reviewComment} placeholder="如:分段与维修/物料记录一致" /></label>
    </div>
    <div class="row-actions">
      <button class="btn success" on:click={() => review(true)}>复核通过并首算</button>
      <button class="btn ghost" on:click={() => review(false)}>驳回</button>
    </div>
  {/if}
</div>
{/if}

<!-- ============ 损失结果与重算批次 ============ -->
{#if calcs.length}
<div class="panel">
  <div class="row-actions" style="justify-content:space-between;">
    <h2 style="margin:0;">产能损失核算结果</h2>
    {#if ev.status === 'REVIEWED'}
      <button class="btn ghost" on:click={recalc}>按当前最新标准重新计算</button>
    {/if}
  </div>

  <div class="note-box estimate">
    ⚠ <strong>估算数据</strong>:以下损失件数按标准节拍推算(分段时长 ÷ 节拍秒/件),<b>非实际减产数据</b>;实际产量以数采/MES 回传为准。
  </div>

  <div class="tabs-sub">
    {#each calcs as c, i}
      <button class:active={batchTab === i} on:click={() => (batchTab = i)}
        class:old-batch={c.superseded}>
        批次 {c.batchNo} · {c.triggerLabel}{c.superseded ? ' · 已被取代' : ''}
      </button>
    {/each}
  </div>

  {#each calcs as c, i}
    {#if batchTab === i}
      <div class="grid cols-3">
        <div class="stat"><div class="k">触发方式</div><div class="v" style="font-size:15px;">{c.triggerLabel}</div></div>
        <div class="stat"><div class="k">标准口径 / 采用版本</div><div class="v" style="font-size:14px;">{c.standardModeLabel}<br><span class="mono" style="font-size:12px;">{c.standardVersionsUsed}</span></div></div>
        <div class="stat"><div class="k">估算总损失</div><div class="v">{c.totalEstimatedLossUnits}<small> 件(估算)</small></div></div>
      </div>

      <table style="margin-top:12px;">
        <thead>
          <tr><th>段</th><th>原因</th><th>时间区间</th><th>时长</th><th>班次</th><th>采用节拍版本</th><th class="right">估算损失(件)</th></tr>
        </thead>
        <tbody>
          {#each c.lines as l}
            <tr>
              <td>{l.lineNo}</td>
              <td><span class="tag {l.reason}">{l.reasonLabel}</span></td>
              <td class="nowrap mono" style="font-size:12px;">{fmt(l.startTime, false)}<br>→ {fmt(l.endTime, false)}</td>
              <td class="nowrap">{duration(l.durationSeconds)}</td>
              <td>{l.shiftLabel}</td>
              <td class="mono">v{l.standardVersion} · {l.cycleSecondsUsed}秒/件</td>
              <td class="right"><strong>{l.estimatedLossUnits}</strong></td>
            </tr>
          {/each}
        </tbody>
        <tfoot>
          <tr><td colspan="6" class="right muted">合计({c.standardModeLabel})</td>
          <td class="right"><strong>{c.totalEstimatedLossUnits}</strong> 件(估算)</td></tr>
        </tfoot>
      </table>

      <h3>计算依据(随批次快照,标准调整后不变)</h3>
      <div class="basis">{c.basisText}</div>
      <div class="muted" style="margin-top:6px;font-size:12px;">
        计算时间:{fmt(c.calculatedAt)} · 计算人:{c.calculatedBy || '—'}
        {#if c.superseded}<span style="color:var(--wait);"> · 该批次已被后续重算取代,但原结果与依据永久保留</span>{/if}
      </div>
    {/if}
  {/each}
</div>
{/if}

{#if ev.status === 'REVIEWED'}
<div class="note-box ok" style="background:#e7f6ee;border:1px solid #a8dcc1;border-radius:8px;padding:10px 14px;">
  ✓ 该事件已由 {ev.reviewedBy || '班长'} 复核{ev.reviewComment ? `(“${ev.reviewComment}”)` : ''},已进入正式统计。
  复核后再改分段会自动退回待复核,旧核算批次仍保留。
</div>
{/if}
{/if}
