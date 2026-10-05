<script>
  import { onMount } from 'svelte';
  import { toast } from './lib/ui.js';
  import EventList from './components/EventList.svelte';
  import EventDetail from './components/EventDetail.svelte';
  import Standards from './components/Standards.svelte';
  import Report from './components/Report.svelte';
  import { api } from './lib/api.js';

  let tab = 'events';
  let selectedEventId = null;
  let equipment = [];

  async function loadEquipment() {
    equipment = await api.equipment();
  }

  function openEvent(id) {
    selectedEventId = id;
    tab = 'detail';
  }

  function goto(t) {
    tab = t;
  }

  onMount(loadEquipment);
</script>

<header class="topbar">
  <div>
    <h1>停机原因拆分与产能损失核算</h1>
    <div class="sub">故障 / 待料 / 换线 分段确认 · 节拍版本留痕 · 估算损失(非实际减产)</div>
  </div>
  <nav class="tabs">
    <button class:active={tab === 'events' || tab === 'detail'} on:click={() => goto('events')}>停机事件</button>
    <button class:active={tab === 'standards'} on:click={() => goto('standards')}>节拍标准</button>
    <button class:active={tab === 'report'} on:click={() => goto('report')}>班次报表</button>
  </nav>
</header>

<main>
  {#if tab === 'events'}
    <EventList {equipment} on:open={(e) => openEvent(e.detail)} on:changed={loadEquipment} />
  {:else if tab === 'detail' && selectedEventId}
    <EventDetail eventId={selectedEventId} on:back={() => goto('events')} on:open={openEvent} />
  {:else if tab === 'standards'}
    <Standards {equipment} on:changed={loadEquipment} />
  {:else if tab === 'report'}
    <Report {equipment} />
  {/if}
</main>

{#if $toast}
  <div class="toast" class:error={$toast.isError}>{$toast.msg}</div>
{/if}
