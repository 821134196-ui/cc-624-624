<script>
  import { onMount } from 'svelte';
  import { api } from '../api.js';
  import { toInputValue, fromInputValue, fmtTime, fmtDuration, STATUS_LABELS } from '../utils.js';
  import Timeline from '../lib/Timeline.svelte';

  let equipment = [];
  let events = [];
  let error = '';
  let notice = '';

  // 登记表单
  let form = { equipmentId: '', start: '', end: '', operatorName: '', note: '' };
  // 起止修改
  let editingId = null;
  let editForm = { start: '', end: '' };

  onMount(load);

  async function load() {
    equipment = await api.listEquipment();
    events = (await api.listEvents()).filter(e => e.status === 'OPEN');
    if (!form.equipmentId && equipment.length) form.equipmentId = equipment[0].id;
  }

  async function submit() {
    error = ''; notice = '';
    try {
      await api.createEvent({
        equipmentId: Number(form.equipmentId),
        startTime: fromInputValue(form.start),
        endTime: fromInputValue(form.end),
        operatorName: form.operatorName,
        note: form.note
      });
      notice = '停机已登记';
      form = { ...form, start: '', end: '', note: '' };
      await load();
    } catch (e) { error = e.message; }
  }

  function startEdit(ev) {
    editingId = ev.id;
    editForm = { start: toInputValue(ev.startTime), end: toInputValue(ev.endTime) };
  }

  async function saveTimes(id) {
    error = ''; notice = '';
    try {
      await api.updateTimes(id, {
        startTime: fromInputValue(editForm.start),
        endTime: fromInputValue(editForm.end)
      });
      notice = '起止时间已更新';
      editingId = null;
      await load();
    } catch (e) { error = e.message; }
  }

  async function markRecovered(ev) {
    error = ''; notice = '';
    try {
      await api.updateTimes(ev.id, { startTime: ev.startTime, endTime: new Date().toISOString() });
      notice = '已登记恢复时间';
      await load();
    } catch (e) { error = e.message; }
  }
</script>

<section>
  <h2>停机登记（操作员）</h2>
  <p class="hint">登记设备停机的起止时间；恢复时间可稍后补登。跨班未恢复的停机沿用同一事件，无需重复登记。</p>

  {#if error}<div class="alert error">{error}</div>{/if}
  {#if notice}<div class="alert ok">{notice}</div>{/if}

  <form class="card form" on:submit|preventDefault={submit}>
    <label>设备
      <select bind:value={form.equipmentId} required>
        {#each equipment as eq}
          <option value={eq.id}>{eq.code} · {eq.name}</option>
        {/each}
      </select>
    </label>
    <label>开始时间
      <input type="datetime-local" bind:value={form.start} required />
    </label>
    <label>恢复时间（仍在停机可留空）
      <input type="datetime-local" bind:value={form.end} />
    </label>
    <label>登记人
      <input bind:value={form.operatorName} placeholder="姓名" required />
    </label>
    <label class="wide">备注
      <input bind:value={form.note} placeholder="现象、位置等（可选）" />
    </label>
    <button type="submit">登记停机</button>
  </form>

  <h3>登记中的事件（{events.length}）</h3>
  {#each events as ev (ev.id)}
    <div class="card event">
      <div class="event-head">
        <strong>{ev.equipmentCode} · {ev.equipmentName}</strong>
        <span class="badge">{STATUS_LABELS[ev.status]}</span>
        {#if ev.crossShift}<span class="badge warn">跨班连续事件</span>{/if}
        <span class="time">{fmtTime(ev.startTime)} → {ev.endTime ? fmtTime(ev.endTime) : '仍在停机'}</span>
        <span class="muted">登记人：{ev.operatorName || '—'} {#if ev.note}· {ev.note}{/if}</span>
      </div>
      {#if ev.endTime}
        <Timeline startTime={ev.startTime} endTime={ev.endTime} segments={ev.segments} />
      {:else}
        <p class="muted">尚未恢复，停机进行中……</p>
      {/if}

      {#if editingId === ev.id}
        <div class="edit-row">
          <input type="datetime-local" bind:value={editForm.start} />
          <span>→</span>
          <input type="datetime-local" bind:value={editForm.end} />
          <button on:click={() => saveTimes(ev.id)}>保存</button>
          <button class="ghost" on:click={() => (editingId = null)}>取消</button>
        </div>
      {:else}
        <div class="actions">
          <button class="ghost" on:click={() => startEdit(ev)}>修改起止</button>
          {#if !ev.endTime}
            <button on:click={() => markRecovered(ev)}>登记恢复（现在）</button>
          {/if}
        </div>
      {/if}
    </div>
  {:else}
    <p class="muted">当前没有登记中的停机事件。</p>
  {/each}
</section>

<style>
  .form { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px; align-items: end; }
  .form .wide { grid-column: 1 / -1; }
  label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: #444; }
  input, select { padding: 7px 9px; border: 1px solid #c9c9d2; border-radius: 6px; font-size: 14px; }
  button { padding: 8px 16px; border: none; border-radius: 6px; background: #2f6fed; color: #fff; cursor: pointer; font-size: 14px; }
  button.ghost { background: #ececf1; color: #333; }
  .event-head { display: flex; flex-wrap: wrap; gap: 10px; align-items: baseline; }
  .time { color: #333; }
  .actions { margin-top: 8px; display: flex; gap: 8px; }
  .edit-row { margin-top: 8px; display: flex; gap: 8px; align-items: center; }
</style>
