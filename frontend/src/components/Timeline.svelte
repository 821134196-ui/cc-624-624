<script>
  import { fmt } from '../lib/time.js';

  // 按分段时长占整段停机的比例横向铺色,实现"分段原因与损失对照"的时间轴
  export let ev;

  const total = (ev) => ev.segments.reduce((a, s) => a + s.durationSeconds, 0);
</script>

<div>
  <div class="timeline">
    {#each ev.segments as s}
      {@const pct = total(ev) ? (s.durationSeconds / total(ev)) * 100 : 0}
      <div class="seg {s.reason}" style="flex: 0 0 {pct}%;"
           title={`${s.reasonLabel} ${fmt(s.startTime)} → ${fmt(s.endTime)} (${Math.round(pct)}%)`}>
        {pct > 7 ? s.reasonLabel : ''}
      </div>
    {/each}
  </div>
  <div class="timeline-scale">
    <span>{fmt(ev.startTime, false)}</span>
    {#each ev.segments as s}
      <span class="muted">{fmt(s.endTime, false)}</span>
    {/each}
  </div>
</div>
