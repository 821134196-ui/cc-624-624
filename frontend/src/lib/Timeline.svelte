<script>
  import { fmtTime, fmtDuration, reasonColor, reasonLabel } from '../utils.js';

  /** 事件起止 */
  export let startTime;
  export let endTime;
  /** 分段：[{start, end, reason, note?}] 或后端 SegmentDto [{startTime,endTime,reason}] */
  export let segments = [];
  /** 可选：每段估算损失（与 segments 同序），报表页用于对照 */
  export let losses = null;

  $: total = startTime && endTime ? (new Date(endTime) - new Date(startTime)) / 1000 : 0;

  function norm(seg) {
    return {
      start: seg.start || seg.startTime,
      end: seg.end || seg.endTime,
      reason: seg.reason,
      note: seg.note
    };
  }

  $: rows = segments.map((raw, i) => {
    const seg = norm(raw);
    const dur = (new Date(seg.end) - new Date(seg.start)) / 1000;
    return {
      ...seg,
      duration: dur,
      pct: total > 0 ? (dur / total) * 100 : 0,
      loss: losses ? losses[i] : null
    };
  });
</script>

<div class="timeline">
  <div class="bar" role="img" aria-label="停机时间轴">
    {#each rows as seg}
      <div
        class="slice"
        style="width:{seg.pct}%;background:{reasonColor(seg.reason)}"
        title="{reasonLabel(seg.reason)} {fmtTime(seg.start)}–{fmtTime(seg.end)}（{fmtDuration(seg.duration)}）"
      ></div>
    {/each}
    {#if rows.length === 0}
      <div class="slice empty" style="width:100%"></div>
    {/if}
  </div>
  <div class="axis">
    <span>{fmtTime(startTime)}</span>
    <span class="total">共 {fmtDuration(total)}</span>
    <span>{fmtTime(endTime)}</span>
  </div>
  {#if rows.length > 0}
    <div class="legend">
      {#each rows as seg, i}
        <div class="legend-item">
          <span class="dot" style="background:{reasonColor(seg.reason)}"></span>
          <span class="legend-label">{i + 1}. {reasonLabel(seg.reason)}</span>
          <span class="legend-time">{fmtTime(seg.start)}–{fmtTime(seg.end)} · {fmtDuration(seg.duration)}</span>
          {#if seg.loss != null}
            <span class="legend-loss">≈{seg.loss} 件</span>
          {/if}
        </div>
      {/each}
    </div>
  {/if}
</div>

<style>
  .timeline { margin: 8px 0; }
  .bar {
    display: flex; height: 26px; border-radius: 6px; overflow: hidden;
    border: 1px solid #d0d0d8; background: #f1f1f4;
  }
  .slice { height: 100%; }
  .slice.empty { background: repeating-linear-gradient(45deg, #ececf0, #ececf0 8px, #e0e0e6 8px, #e0e0e6 16px); }
  .axis {
    display: flex; justify-content: space-between;
    font-size: 12px; color: #666; margin-top: 4px;
  }
  .axis .total { font-weight: 600; color: #333; }
  .legend { margin-top: 6px; display: flex; flex-direction: column; gap: 3px; }
  .legend-item { display: flex; align-items: center; gap: 8px; font-size: 13px; }
  .dot { width: 10px; height: 10px; border-radius: 3px; flex: none; }
  .legend-label { font-weight: 600; min-width: 64px; }
  .legend-time { color: #666; }
  .legend-loss { margin-left: auto; color: #b03a2e; font-weight: 600; }
</style>
