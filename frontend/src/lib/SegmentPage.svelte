<script>
  import { onMount } from 'svelte';
  import { api } from '../api.js';
  import {
    toInputValue, fromInputValue, fmtTime, fmtDuration,
    REASONS, reasonLabel, STATUS_LABELS
  } from '../utils.js';
  import Timeline from '../lib/Timeline.svelte';

  let events = [];
  let selected = null;        // 当前编辑的事件（含分段）
  let rows = [];              // 编辑中的分段行
  let calculations = [];      // 已复核事件的核算历史
  let reviewerName = '';
  let error = '';
  let notice = '';
  let clientError = '';

  onMount(load);

  async function load(keepSelection = false) {
    events = await api.listEvents();
    if (keepSelection && selected) {
      const fresh = events.find(e => e.id === selected.id);
      if (fresh) await select(fresh, true);
    }
  }

  async function select(ev, silent = false) {
    if (!silent) { error = ''; notice = ''; }
    selected = await api.getEvent(ev.id);
    rows = selected.segments.map(s => ({
      reason: s.reason,
      start: toInputValue(s.startTime),
      end: toInputValue(s.endTime),
      note: s.note || ''
    }));
    if (rows.length === 0 && selected.endTime) {
      // 默认整段记为故障，便于班长在此基础上拆分
      rows = [{ reason: 'FAULT', start: toInputValue(selected.startTime), end: toInputValue(selected.endTime), note: '' }];
    }
    calculations = selected.status === 'REVIEWED' ? await api.calculations(selected.id) : [];
    checkClient();
  }

  function addRow(afterIndex) {
    // 在指定行之后插入：把该行从中间劈开
    const row = rows[afterIndex];
    if (!row || !row.start || !row.end) return;
    const mid = new Date((new Date(fromInputValue(row.start)).getTime() + new Date(fromInputValue(row.end)).getTime()) / 2);
    const midVal = toInputValue(mid.toISOString());
    const tail = { reason: row.reason, start: midVal, end: row.end, note: '' };
    rows = [
      ...rows.slice(0, afterIndex),
      { ...row, end: midVal },
      tail,
      ...rows.slice(afterIndex + 1)
    ];
    checkClient();
  }

  function removeRow(i) {
    rows = rows.filter((_, idx) => idx !== i);
    checkClient();
  }

  /** 客户端守恒预检（服务端仍会强校验） */
  function checkClient() {
    clientError = '';
    if (!selected || !selected.endTime) return;
    if (rows.length === 0) { clientError = '至少需要一段'; return; }
    const evStart = fromInputValue(toInputValue(selected.startTime));
    const evEnd = fromInputValue(toInputValue(selected.endTime));
    let cursor = evStart;
    for (let i = 0; i < rows.length; i++) {
      const r = rows[i];
      const s = fromInputValue(r.start);
      const e = fromInputValue(r.end);
      if (!s || !e || s >= e) { clientError = `第 ${i + 1} 段起止时间无效`; return; }
      if (s !== cursor) { clientError = `第 ${i + 1} 段与前一段之间存在重叠或空白`; return; }
      cursor = e;
    }
    if (cursor !== evEnd) { clientError = '分段末尾未覆盖到停机结束时间'; return; }
  }

  async function submitSegments() {
    error = ''; notice = '';
    try {
      await api.saveSegments(selected.id, rows.map(r => ({
        start: fromInputValue(r.start),
        end: fromInputValue(r.end),
        reason: r.reason,
        note: r.note
      })));
      notice = '分段已保存，等待复核';
      await load(true);
    } catch (e) { error = e.message; }
  }

  async function doReview() {
    error = ''; notice = '';
    if (!reviewerName.trim()) { error = '请填写复核人姓名'; return; }
    try {
      await api.review(selected.id, reviewerName.trim());
      notice = '复核通过，事件已进入正式统计';
      reviewerName = '';
      await load(true);
    } catch (e) { error = e.message; }
  }

  async function doReopen() {
    error = ''; notice = '';
    try {
      await api.reopen(selected.id);
      notice = '已退回，可重新分段';
      await load(true);
    } catch (e) { error = e.message; }
  }

  async function doRecalculate() {
    error = ''; notice = '';
    try {
      await api.recalculate(selected.id);
      notice = '已按当前节拍标准重新核算，历史结果保留';
      await load(true);
    } catch (e) { error = e.message; }
  }

  $: editable = selected && selected.status !== 'REVIEWED';
  $: previewSegments = rows
    .filter(r => r.start && r.end)
    .map(r => ({ start: fromInputValue(r.start), end: fromInputValue(r.end), reason: r.reason }));
</script>

<section>
  <h2>时间轴分段确认（班长）</h2>
  <p class="hint">在同一时间轴上把整次停机拆分为故障 / 待料 / 换线等原因段。各段不得重叠或留空白，合计必须等于整次停机时长；复核通过后进入正式统计。</p>

  {#if error}<div class="alert error">{error}</div>{/if}
  {#if notice}<div class="alert ok">{notice}</div>{/if}

  <div class="layout">
    <aside class="card list">
      <h3>停机事件</h3>
      {#each events as ev (ev.id)}
        <button
          class="item"
          class:active={selected && selected.id === ev.id}
          on:click={() => select(ev)}
        >
          <span class="item-title">{ev.equipmentCode} · {fmtTime(ev.startTime)}</span>
          <span class="item-sub">
            <span class="badge status-{ev.status.toLowerCase()}">{STATUS_LABELS[ev.status]}</span>
            {#if ev.crossShift}<span class="badge warn">跨班</span>{/if}
            {ev.endTime ? fmtDuration(ev.durationSeconds) : '进行中'}
          </span>
        </button>
      {:else}
        <p class="muted">暂无停机事件</p>
      {/each}
    </aside>

    {#if selected}
      <div class="card editor">
        <div class="event-head">
          <strong>{selected.equipmentCode} · {selected.equipmentName}</strong>
          <span class="badge status-{selected.status.toLowerCase()}">{STATUS_LABELS[selected.status]}</span>
          {#if selected.crossShift}<span class="badge warn">跨班连续事件</span>{/if}
        </div>
        <p class="muted">
          {fmtTime(selected.startTime)} → {selected.endTime ? fmtTime(selected.endTime) : '仍在停机'}
          {#if selected.durationSeconds != null}· 共 {fmtDuration(selected.durationSeconds)}{/if}
          · 归属班次 {selected.shiftKey}
        </p>

        {#if !selected.endTime}
          <p class="alert warn">停机尚未结束，请先在“停机登记”页补登恢复时间，再进行分段。</p>
        {:else}
          <Timeline
            startTime={selected.startTime}
            endTime={selected.endTime}
            segments={editable ? previewSegments : selected.segments}
            losses={selected.calculation ? selected.calculation.segments.map(s => s.estimatedLoss) : null}
          />

          {#if editable}
            <table class="seg-table">
              <thead>
                <tr><th>#</th><th>原因</th><th>开始</th><th>结束</th><th>时长</th><th>备注</th><th></th></tr>
              </thead>
              <tbody>
                {#each rows as row, i}
                  <tr>
                    <td>{i + 1}</td>
                    <td>
                      <select bind:value={row.reason} on:change={checkClient}>
                        {#each REASONS as r}
                          <option value={r.code}>{r.label}</option>
                        {/each}
                      </select>
                    </td>
                    <td><input type="datetime-local" bind:value={row.start} on:input={checkClient} /></td>
                    <td><input type="datetime-local" bind:value={row.end} on:input={checkClient} /></td>
                    <td class="muted">
                      {#if row.start && row.end}
                        {fmtDuration((new Date(fromInputValue(row.end)) - new Date(fromInputValue(row.start))) / 1000)}
                      {/if}
                    </td>
                    <td><input bind:value={row.note} placeholder="可选" /></td>
                    <td class="row-actions">
                      <button class="ghost" title="在此段中间拆分" on:click={() => addRow(i)}>拆分</button>
                      {#if rows.length > 1}
                        <button class="ghost danger" on:click={() => removeRow(i)}>删除</button>
                      {/if}
                    </td>
                  </tr>
                {/each}
              </tbody>
            </table>
            {#if clientError}
              <div class="alert error">分段未守恒：{clientError}</div>
            {/if}
            <div class="actions">
              <button on:click={submitSegments} disabled={!!clientError}>保存分段</button>
              {#if selected.status === 'PENDING_REVIEW'}
                <span class="review-box">
                  <input bind:value={reviewerName} placeholder="复核人姓名" />
                  <button class="confirm" on:click={doReview}>复核通过</button>
                  <button class="ghost" on:click={doReopen}>退回重分</button>
                </span>
              {/if}
            </div>
          {:else}
            <!-- 已复核：展示核算与历史，支持标准变更后重算 -->
            {#if selected.calculation}
              <div class="calc">
                <h4>当前核算（估算值，非实际减产）</h4>
                <p>
                  采用节拍标准 <strong>{selected.calculation.standardVersion}</strong>
                  （{selected.calculation.taktSeconds} 秒/件）·
                  估算损失 <strong class="loss">{selected.calculation.totalEstimatedLoss} 件</strong>
                </p>
              </div>
            {/if}
            <div class="actions">
              <button on:click={doRecalculate}>按当前节拍标准重算</button>
            </div>
            {#if calculations.length > 0}
              <h4>核算历史（{calculations.length}）</h4>
              <table class="seg-table">
                <thead>
                  <tr><th>时间</th><th>节拍版本</th><th>节拍(秒/件)</th><th>估算损失</th><th>状态</th></tr>
                </thead>
                <tbody>
                  {#each calculations as c}
                    <tr>
                      <td>{fmtTime(c.calculatedAt)}</td>
                      <td>{c.standardVersion}</td>
                      <td>{c.taktSeconds}</td>
                      <td>{c.totalEstimatedLoss} 件</td>
                      <td>{c.active ? '当前生效' : '已归档'}</td>
                    </tr>
                  {/each}
                </tbody>
              </table>
            {/if}
          {/if}
        {/if}
      </div>
    {:else}
      <div class="card editor placeholder"><p class="muted">从左侧选择一个停机事件开始分段。</p></div>
    {/if}
  </div>
</section>

<style>
  .layout { display: grid; grid-template-columns: 300px 1fr; gap: 16px; align-items: start; }
  .list { max-height: 70vh; overflow: auto; }
  .item {
    display: flex; flex-direction: column; gap: 4px; width: 100%; text-align: left;
    padding: 8px 10px; margin-bottom: 6px; border: 1px solid #e0e0e6; border-radius: 8px;
    background: #fff; cursor: pointer;
  }
  .item.active { border-color: #2f6fed; background: #f2f6ff; }
  .item-title { font-weight: 600; font-size: 13px; }
  .item-sub { display: flex; gap: 6px; align-items: center; font-size: 12px; color: #666; }
  .editor { min-height: 300px; }
  .editor.placeholder { display: flex; align-items: center; justify-content: center; }
  .seg-table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 13px; }
  .seg-table th, .seg-table td { border-bottom: 1px solid #ececf1; padding: 6px 8px; text-align: left; }
  .seg-table input, .seg-table select { padding: 5px 7px; border: 1px solid #c9c9d2; border-radius: 5px; font-size: 13px; }
  .row-actions { white-space: nowrap; }
  .row-actions button { padding: 4px 10px; font-size: 12px; margin-right: 4px; }
  .actions { margin-top: 12px; display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
  .review-box { display: flex; gap: 8px; align-items: center; }
  .review-box input { padding: 7px 9px; border: 1px solid #c9c9d2; border-radius: 6px; }
  button { padding: 8px 16px; border: none; border-radius: 6px; background: #2f6fed; color: #fff; cursor: pointer; }
  button:disabled { background: #b9c6e8; cursor: not-allowed; }
  button.ghost { background: #ececf1; color: #333; }
  button.ghost.danger { color: #b03a2e; }
  button.confirm { background: #1a9e54; }
  .calc { background: #fdf6ec; border: 1px solid #f0e0c0; border-radius: 8px; padding: 10px 14px; margin-top: 10px; }
  .loss { color: #b03a2e; }
  h4 { margin: 14px 0 6px; }
</style>
