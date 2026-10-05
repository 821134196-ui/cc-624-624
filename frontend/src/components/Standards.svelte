<script>
  import { onMount } from 'svelte';
  import { api } from '../lib/api.js';
  import { fmt, toLocalInput, localInputToIso } from '../lib/time.js';
  import { showToast } from '../lib/ui.js';

  export let equipment = [];

  let selected = null;
  let standards = [];
  let showForm = false;
  let autoSelected = false;
  let form = {
    cycleSeconds: '',
    effectiveFrom: toLocalInput(new Date().toISOString()).slice(0, 16),
    changedBy: '工程师',
    changeReason: ''
  };

  async function select(e) {
    selected = e;
    standards = await api.standards(e.id);
  }

  async function publish() {
    try {
      const secs = Number(form.cycleSeconds);
      if (!(secs > 0)) throw new Error('标准节拍必须为正数(秒/件)');
      await api.publishStandard(selected.id, {
        cycleSeconds: secs,
        effectiveFrom: localInputToIso(form.effectiveFrom),
        changedBy: form.changedBy,
        changeReason: form.changeReason
      });
      showToast(`已发布 v${(standards[0]?.version || 0) + 1},旧版本保留为历史版本`);
      showForm = false;
      form.cycleSeconds = '';
      form.changeReason = '';
      await select(selected);
    } catch (e) { showToast(e.message, true); }
  }

  onMount(() => { if (equipment.length) select(equipment[0]); });
  // 设备列表晚于组件加载时,自动选中第一台
  $: if (!autoSelected && equipment.length && !selected) {
    autoSelected = true;
    select(equipment[0]);
  }
</script>

<div class="panel">
  <h2>节拍标准与版本管理</h2>
  <div class="note-box info">
    每次调整都会生成<b>新版本</b>,旧版本自动置为「已替代」但永不删除。
    历史核算永久引用当时版本;可对已复核事件用新版本<b>重新计算</b>,原结果与依据仍可查。
  </div>

  <div class="tabs-sub">
    {#each equipment as e}
      <button class:active={selected?.id === e.id} on:click={() => select(e)}>
        {e.code} · {e.name}
      </button>
    {/each}
  </div>

  {#if selected}
    <div class="row-actions" style="justify-content:space-between;margin:6px 0 12px;">
      <div>
        当前生效:
        {#if standards[0]?.status === 'ACTIVE'}
          <strong class="mono">v{standards[0].version} = {standards[0].cycleSeconds} 秒/件</strong>
        {:else}
          <span class="muted">尚未建立标准</span>
        {/if}
      </div>
      <button class="btn" on:click={() => (showForm = !showForm)}>{showForm ? '取消' : '+ 发布新版本(调整标准)'}</button>
    </div>

    {#if showForm}
      <div class="note-box info">
        <div class="grid cols-3">
          <label class="field"><span>新标准节拍(秒/件)</span><input type="number" step="0.1" bind:value={form.cycleSeconds} /></label>
          <label class="field"><span>生效时间(当班/历史口径按此时点取版本)</span><input type="datetime-local" step="1" bind:value={form.effectiveFrom} /></label>
          <label class="field"><span>调整人</span><input bind:value={form.changedBy} /></label>
        </div>
        <label class="field"><span>调整原因</span><input bind:value={form.changeReason} placeholder="如:模具改善后提速" /></label>
        <button class="btn" on:click={publish}>发布新版本</button>
      </div>
    {/if}

    <table>
      <thead>
        <tr><th>版本</th><th>节拍(秒/件)</th><th>生效时间</th><th>状态</th><th>替代来源</th><th>调整人</th><th>调整原因</th><th>发布时间</th></tr>
      </thead>
      <tbody>
        {#each standards as s}
          <tr>
            <td class="mono"><strong>v{s.version}</strong></td>
            <td class="mono">{s.cycleSeconds}</td>
            <td class="nowrap">{fmt(s.effectiveFrom)}</td>
            <td>
              {#if s.status === 'ACTIVE'}
                <span class="badge reviewed">生效中</span>
              {:else}
                <span class="badge registered">已被 v{s.supersededByVersion} 替代</span>
              {/if}
            </td>
            <td>{s.supersededByVersion ? `→ v${s.supersededByVersion}` : '—'}</td>
            <td>{s.changedBy || '—'}</td>
            <td class="muted">{s.changeReason || '—'}</td>
            <td class="nowrap muted">{fmt(s.createdAt, false)}</td>
          </tr>
        {/each}
      </tbody>
    </table>
  {/if}
</div>
