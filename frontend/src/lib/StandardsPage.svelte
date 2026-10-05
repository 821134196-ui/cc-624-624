<script>
  import { onMount } from 'svelte';
  import { api } from '../api.js';
  import { toInputValue, fromInputValue, fmtTime } from '../utils.js';

  let equipment = [];
  let standards = [];
  let error = '';
  let notice = '';
  let form = { equipmentId: '', version: '', taktSeconds: '', effectiveFrom: '', note: '' };

  onMount(load);

  async function load() {
    equipment = await api.listEquipment();
    standards = await api.listStandards();
    if (!form.equipmentId && equipment.length) form.equipmentId = equipment[0].id;
  }

  async function submit() {
    error = ''; notice = '';
    try {
      await api.createStandard({
        equipmentId: Number(form.equipmentId),
        version: form.version.trim(),
        taktSeconds: Number(form.taktSeconds),
        effectiveFrom: fromInputValue(form.effectiveFrom),
        note: form.note
      });
      notice = `标准 ${form.version} 已发布；已复核事件可在“分段确认”页重算，历史结果保留`;
      form = { ...form, version: '', taktSeconds: '', note: '' };
      await load();
    } catch (e) { error = e.message; }
  }

  $: grouped = equipment.map(eq => ({
    equipment: eq,
    versions: standards.filter(s => s.equipmentId === eq.id)
  }));
</script>

<section>
  <h2>标准节拍管理</h2>
  <p class="hint">节拍按版本管理，旧版本保留。损失核算记录所采用的版本与节拍值，标准调整后重算仍可查到原结果及其依据。</p>

  {#if error}<div class="alert error">{error}</div>{/if}
  {#if notice}<div class="alert ok">{notice}</div>{/if}

  <form class="card form" on:submit|preventDefault={submit}>
    <label>设备
      <select bind:value={form.equipmentId}>
        {#each equipment as eq}
          <option value={eq.id}>{eq.code} · {eq.name}</option>
        {/each}
      </select>
    </label>
    <label>版本号
      <input bind:value={form.version} placeholder="如 v3" required />
    </label>
    <label>节拍（秒/件）
      <input type="number" step="0.1" min="0.1" bind:value={form.taktSeconds} required />
    </label>
    <label>生效时间
      <input type="datetime-local" bind:value={form.effectiveFrom} required />
    </label>
    <label class="wide">说明
      <input bind:value={form.note} placeholder="调整原因（可选）" />
    </label>
    <button type="submit">发布新版本</button>
  </form>

  {#each grouped as g}
    <div class="card">
      <h3>{g.equipment.code} · {g.equipment.name}</h3>
      {#if g.versions.length > 0}
        <table>
          <thead><tr><th>版本</th><th>节拍（秒/件）</th><th>生效时间</th><th>说明</th></tr></thead>
          <tbody>
            {#each g.versions as s}
              <tr>
                <td><strong>{s.version}</strong></td>
                <td>{s.taktSeconds}</td>
                <td>{fmtTime(s.effectiveFrom)}</td>
                <td class="muted">{s.note || '—'}</td>
              </tr>
            {/each}
          </tbody>
        </table>
      {:else}
        <p class="muted">尚未配置节拍标准。</p>
      {/if}
    </div>
  {/each}
</section>

<style>
  .form { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; align-items: end; }
  .form .wide { grid-column: 1 / -1; }
  label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: #444; }
  input, select { padding: 7px 9px; border: 1px solid #c9c9d2; border-radius: 6px; font-size: 14px; }
  button { padding: 8px 16px; border: none; border-radius: 6px; background: #2f6fed; color: #fff; cursor: pointer; }
  table { width: 100%; border-collapse: collapse; font-size: 14px; }
  th, td { border-bottom: 1px solid #ececf1; padding: 7px 9px; text-align: left; }
</style>
