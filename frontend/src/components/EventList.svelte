<script>
  import { createEventDispatcher, onMount } from 'svelte';
  import { api } from '../lib/api.js';
  import { fmt, duration, toLocalInput, localInputToIso } from '../lib/time.js';
  import { showToast, STATUS_LABEL } from '../lib/ui.js';

  export let equipment = [];
  const dispatch = createEventDispatcher();

  let events = [];
  let showRegister = false;
  let form = { equipmentId: '', startTime: toLocalInput(new Date().toISOString()).slice(0, 16), endTime: '', registeredBy: '' };

  async function load() {
    events = await api.events();
  }

  async function submitRegister() {
    try {
      const body = {
        equipmentId: Number(form.equipmentId),
        startTime: localInputToIso(form.startTime),
        endTime: form.endTime ? localInputToIso(form.endTime) : null,
        registeredBy: form.registeredBy || '操作员'
      };
      const ev = await api.register(body);
      showToast(`已登记停机事件 ${ev.eventCode}`);
      showRegister = false;
      await load();
    } catch (e) {
      showToast(e.message, true);
    }
  }

  async function quickResolve(ev) {
    try {
      await api.resolve(ev.id, { endTime: new Date().toISOString() });
      showToast(`事件 ${ev.eventCode} 已登记恢复(沿用同一事件)`);
      await load();
    } catch (e) {
      showToast(e.message, true);
    }
  }

  onMount(load);
</script>

<div class="panel">
  <div class="row-actions" style="justify-content: space-between;">
    <h2 style="margin:0;">停机事件台账</h2>
    <button class="btn" on:click={() => (showRegister = !showRegister)}>
      {showRegister ? '取消' : '+ 操作员登记停机'}
    </button>
  </div>

  {#if showRegister}
    <div class="note-box info" style="margin-top:14px;">
      <div style="font-weight:600;margin-bottom:8px;">操作员登记停机起止时间</div>
      <div class="grid cols-3">
        <label class="field">
          <span>设备</span>
          <select bind:value={form.equipmentId}>
            <option value="">请选择设备</option>
            {#each equipment as e}
              <option value={e.id}>{e.code} · {e.name}</option>
            {/each}
          </select>
        </label>
        <label class="field">
          <span>停机开始时间</span>
          <input type="datetime-local" step="1" bind:value={form.startTime} />
        </label>
        <label class="field">
          <span>恢复时间(可暂不填,设备恢复后补录;跨班仍为同一事件)</span>
          <input type="datetime-local" step="1" bind:value={form.endTime} placeholder="未恢复可留空" />
        </label>
      </div>
      <label class="field" style="max-width:240px;">
        <span>登记人</span>
        <input bind:value={form.registeredBy} placeholder="操作员姓名" />
      </label>
      <div class="muted" style="font-size:12px;margin-bottom:10px;">
        同一设备存在未恢复事件时不可重复登记 —— 防止一次停机被拆成两次独立故障重复统计。
      </div>
      <button class="btn" on:click={submitRegister}>提交登记</button>
    </div>
  {/if}

  <table style="margin-top:14px;">
    <thead>
      <tr>
        <th>事件单号</th>
        <th>设备</th>
        <th>停机开始</th>
        <th>恢复时间</th>
        <th>总时长</th>
        <th>跨越班次</th>
        <th>状态</th>
        <th>守恒</th>
        <th class="right">操作</th>
      </tr>
    </thead>
    <tbody>
      {#each events as ev}
        <tr class="clickable" on:click={() => dispatch('open', ev.id)}>
          <td class="mono">{ev.eventCode}</td>
          <td>{ev.equipmentCode} <span class="muted">{ev.equipmentName}</span></td>
          <td class="nowrap">{fmt(ev.startTime)}</td>
          <td class="nowrap">
            {#if ev.open}
              <span class="badge open">未恢复 · 跨班沿用</span>
            {:else}
              {fmt(ev.endTime)}
            {/if}
          </td>
          <td class="nowrap">{duration(ev.totalDurationSeconds)}</td>
          <td>
            {#each ev.shiftLabels as l, i}
              {#if i > 0}<span class="muted"> →</span>{/if}
              <span style="font-size:12px;">{l.split(' ')[1] || l}</span>
            {/each}
          </td>
          <td><span class="badge {ev.status.toLowerCase()}">{ev.statusLabel}</span></td>
          <td>
            {#if ev.open}
              <span class="muted">—</span>
            {:else if ev.conservationValid}
              <span style="color:var(--ok);font-weight:600;">✓ 守恒</span>
            {:else}
              <span style="color:var(--failure);font-weight:600;">{ev.coveredSeconds > 0 ? '✗ 不守恒' : '未分段'}</span>
            {/if}
          </td>
          <td class="right">
            <div class="row-actions" style="justify-content:flex-end;" on:click|stopPropagation>
              {#if ev.open}
                <button class="btn small ghost" on:click={() => quickResolve(ev)}>登记恢复</button>
              {/if}
              <button class="btn small" on:click={() => dispatch('open', ev.id)}>
                {ev.status === 'REVIEWED' ? '查看结果' : '时间轴分段'}
              </button>
            </div>
          </td>
        </tr>
      {/each}
    </tbody>
  </table>
</div>
