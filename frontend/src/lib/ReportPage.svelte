<script>
  import { onMount } from 'svelte';
  import { api } from '../api.js';
  import { fmtTime, fmtDuration, todayInWorkshop } from '../utils.js';
  import Timeline from '../lib/Timeline.svelte';

  let date = todayInWorkshop();
  let shift = 'DAY';
  let report = null;
  let error = '';

  onMount(load);

  async function load() {
    error = '';
    try {
      report = await api.shiftReport(date, shift);
    } catch (e) {
      error = e.message;
      report = null;
    }
  }

  $: productionByEquipment = report
    ? Object.fromEntries(report.production.map(p => [p.equipmentId, p]))
    : {};
</script>

<section>
  <h2>班次统计报表</h2>
  <p class="hint">仅统计已复核事件；损失按当班适用的标准节拍估算，<strong>为估算值，不能当作实际减产数据</strong>。跨班事件只计入起始班次一次。</p>

  <div class="card filters">
    <label>日期 <input type="date" bind:value={date} on:change={load} /></label>
    <label>班次
      <select bind:value={shift} on:change={load}>
        <option value="DAY">早班（08:00–20:00）</option>
        <option value="NIGHT">夜班（20:00–次日08:00）</option>
      </select>
    </label>
  </div>

  {#if error}<div class="alert error">{error}</div>{/if}

  {#if report}
    <div class="summary">
      <div class="stat card">
        <div class="stat-num">{report.eventCount}</div>
        <div class="stat-label">已复核停机事件</div>
      </div>
      <div class="stat card">
        <div class="stat-num">{fmtDuration(report.totalDowntimeSeconds)}</div>
        <div class="stat-label">停机总时长</div>
      </div>
      <div class="stat card estimate">
        <div class="stat-num">≈{report.totalEstimatedLoss} 件</div>
        <div class="stat-label">估算产能损失 <span class="estimate-tag">估算值</span></div>
      </div>
    </div>

    <div class="card">
      <h3>按原因汇总 <span class="estimate-tag">估算值</span></h3>
      {#if report.reasonTotals.length > 0}
        <table>
          <thead><tr><th>原因</th><th>时长</th><th>估算损失（件）</th></tr></thead>
          <tbody>
            {#each report.reasonTotals as r}
              <tr>
                <td>{r.reasonLabel}</td>
                <td>{fmtDuration(r.durationSeconds)}</td>
                <td>≈{r.estimatedLoss}</td>
              </tr>
            {/each}
          </tbody>
        </table>
      {:else}
        <p class="muted">本班暂无已复核的停机记录。</p>
      {/if}
    </div>

    {#each report.events as ev (ev.id)}
      <div class="card event">
        <div class="event-head">
          <strong>{ev.equipmentCode} · {ev.equipmentName}</strong>
          {#if ev.crossShift}<span class="badge warn">跨班连续事件</span>{/if}
          <span class="muted">{fmtTime(ev.startTime)} → {fmtTime(ev.endTime)} · {fmtDuration(ev.durationSeconds)}</span>
          {#if productionByEquipment[ev.equipmentId]}
            <span class="prod">当班实际产量（{productionByEquipment[ev.equipmentId].source}）：{productionByEquipment[ev.equipmentId].actualOutput} 件</span>
          {/if}
        </div>
        <Timeline
          startTime={ev.startTime}
          endTime={ev.endTime}
          segments={ev.segments}
          losses={ev.calculation ? ev.calculation.segments.map(s => s.estimatedLoss) : null}
        />
        {#if ev.calculation}
          <p class="muted calc-note">
            估算依据：节拍标准 {ev.calculation.standardVersion}（{ev.calculation.taktSeconds} 秒/件），
            合计 ≈{ev.calculation.totalEstimatedLoss} 件 · <span class="estimate-tag">估算值</span>
          </p>
        {/if}
      </div>
    {/each}

    {#if report.excludedEvents.length > 0}
      <div class="card excluded">
        <h3>未计入统计的事件（{report.excludedEvents.length}）</h3>
        <p class="muted">以下事件尚未复核，按规则不进入正式统计：</p>
        <ul>
          {#each report.excludedEvents as ev}
            <li>{ev.equipmentCode} · {fmtTime(ev.startTime)} 起 · 状态：{ev.status === 'OPEN' ? '登记中' : '待复核'}</li>
          {/each}
        </ul>
      </div>
    {/if}

    <div class="card">
      <h3>产量回传（本地模拟接口）</h3>
      <table>
        <thead><tr><th>设备</th><th>当班实际产量（件）</th><th>来源</th></tr></thead>
        <tbody>
          {#each report.production as p}
            <tr><td>{p.code} · {p.name}</td><td>{p.actualOutput}</td><td>{p.source}</td></tr>
          {/each}
        </tbody>
      </table>
    </div>
  {/if}
</section>

<style>
  .filters { display: flex; gap: 16px; margin-bottom: 14px; }
  .filters label { display: flex; align-items: center; gap: 8px; font-size: 14px; }
  .filters input, .filters select { padding: 6px 9px; border: 1px solid #c9c9d2; border-radius: 6px; }
  .summary { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin-bottom: 14px; }
  .stat { text-align: center; padding: 18px; }
  .stat-num { font-size: 26px; font-weight: 700; }
  .stat-label { color: #666; font-size: 13px; margin-top: 4px; }
  .stat.estimate { background: #fdf6ec; border-color: #f0e0c0; }
  table { width: 100%; border-collapse: collapse; font-size: 14px; }
  th, td { border-bottom: 1px solid #ececf1; padding: 7px 9px; text-align: left; }
  .event-head { display: flex; flex-wrap: wrap; gap: 10px; align-items: baseline; }
  .prod { color: #1a6e3c; font-size: 13px; }
  .calc-note { margin-top: 6px; font-size: 13px; }
  .excluded { background: #fafafa; }
  .excluded ul { margin: 6px 0; padding-left: 20px; }
</style>
