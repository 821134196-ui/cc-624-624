<script>
  import { onMount } from 'svelte';
  import { api } from '../lib/api.js';
  import { fmt, duration, minutes } from '../lib/time.js';
  import { showToast } from '../lib/ui.js';

  export let equipment = [];

  const today = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  let date = `${today.getFullYear()}-${pad(today.getMonth() + 1)}-${pad(today.getDate())}`;
  let shift = '早班';
  let report = null;
  let loading = false;

  const SHIFTS = ['早班', '夜班'];

  async function load() {
    loading = true;
    try {
      report = await api.report(date, shift === '全天' ? '' : shift);
    } catch (e) {
      showToast(e.message, true);
    } finally {
      loading = false;
    }
  }

  async function backfillAll() {
    // 本地模拟产量回传:为该班区间逐分钟生成
    try {
      let total = 0;
      for (const e of equipment) {
        const from = report ? report.from : null;
        const to = report ? report.to : null;
        if (!from) continue;
        const r = await api.backfill(e.id, from, to);
        total += r.createdBuckets;
      }
      showToast(`模拟产量回传完成:新增/确认 ${total} 个分钟桶(SIMULATED)`);
      await load();
    } catch (e) { showToast(e.message, true); }
  }

  onMount(load);
</script>

<div class="panel">
  <div class="row-actions" style="justify-content:space-between;">
    <h2 style="margin:0;">班次停机与估算产能损失报表</h2>
    <div class="row-actions">
      <input type="date" bind:value={date} style="max-width:170px;" />
      <select bind:value={shift} style="max-width:120px;">
        {#each SHIFTS as s}<option>{s}</option>{/each}
        <option>全天</option>
      </select>
      <button class="btn" on:click={load}>查询</button>
      <button class="btn ghost" on:click={backfillAll}>生成本班模拟产量</button>
    </div>
  </div>

  <div class="note-box estimate" style="margin-top:12px;">
    ⚠ <strong>本报表全部损失数字均为估算</strong>:按当班标准节拍推算的理论产能损失,
    <b>不能当作实际减产数据</b>。实际产量以数采/MES 回传为准。
    报表仅统计<b>班长已复核</b>的停机事件,未复核/待分段事件一律不混入。
  </div>

  {#if report}
    <div class="muted" style="margin:8px 0;">
      统计班次:{report.shiftLabel} · 区间 <span class="mono">{fmt(report.from, false)} → {fmt(report.to, false)}</span>
    </div>

    <div class="grid cols-3">
      <div class="stat"><div class="k">纳入统计的停机事件(已复核)</div><div class="v">{report.eventCount}<small> 起</small></div></div>
      <div class="stat"><div class="k">停机总时长(本班区间内分段)</div><div class="v" style="font-size:18px;">{duration(report.totalDurationSeconds)} <small>({minutes(report.totalDurationSeconds)} 分)</small></div></div>
      <div class="stat"><div class="k">估算产能损失合计</div><div class="v" style="color:var(--failure);">{report.totalEstimatedLossUnits} <small>件(估算)</small></div></div>
    </div>

    <h3>按停机原因拆分(同一事件内故障/待料/换线分别归集)</h3>
    <table>
      <thead>
        <tr><th>原因</th><th>分段数</th><th>时长</th><th>占停机时长</th><th class="right">估算损失(件)</th></tr>
      </thead>
      <tbody>
        {#each report.byReason as row}
          <tr>
            <td><span class="tag {row.reason}">{row.reasonLabel}</span></td>
            <td>{row.segmentCount}</td>
            <td class="nowrap">{duration(row.durationSeconds)}</td>
            <td>
              {report.totalDurationSeconds ? Math.round((row.durationSeconds / report.totalDurationSeconds) * 100) : 0}%
              <div style="height:6px;background:#eef1f5;border-radius:4px;overflow:hidden;width:160px;display:inline-block;vertical-align:middle;margin-left:8px;">
                <div style="height:100%;width:{report.totalDurationSeconds ? (row.durationSeconds / report.totalDurationSeconds) * 100 : 0}%;background:var(--brand);"></div>
              </div>
            </td>
            <td class="right"><strong>{row.estimatedLossUnits}</strong></td>
          </tr>
        {/each}
      </tbody>
    </table>

    <h3>本地模拟实际产量(仅作对照,非真实数采)</h3>
    {#if report.simulatedActual.length}
      <table>
        <thead><tr><th>设备</th><th>名称</th><th class="right">本班模拟产量(件)</th><th>数据来源</th></tr></thead>
        <tbody>
          {#each report.simulatedActual as a}
            <tr>
              <td class="mono">{a.equipmentCode}</td>
              <td>{a.equipmentName}</td>
              <td class="right">{a.simulatedPieces}</td>
              <td><span class="badge segmented">{a.source}</span></td>
            </tr>
          {/each}
        </tbody>
      </table>
      <div class="muted" style="font-size:12px;margin-top:6px;">
        注:模拟产量与估算损失并列展示仅为方便对照,二者口径不同,不得据此回填实际减产。
      </div>
    {:else}
      <div class="muted">本班暂无模拟产量数据,可点击右上角「生成本班模拟产量」。</div>
    {/if}

    <div class="note-box info" style="margin-top:14px;">
      <strong>口径说明</strong>
      <ul>
        <li>跨班停机沿用同一事件:报表按分段归属班次区间归集,同一事件不会在两个班次被重复统计为两次故障。</li>
        <li>估算损失 = 分段秒数 ÷ 该设备当班当时生效节拍(秒/件);每个事件详情页留有逐版计算依据。</li>
        <li>{report.disclaimer}</li>
      </ul>
    </div>
  {/if}
</div>
