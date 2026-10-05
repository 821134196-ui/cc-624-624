<script>
  import { onMount } from 'svelte';
  import RegisterPage from './lib/RegisterPage.svelte';
  import SegmentPage from './lib/SegmentPage.svelte';
  import ReportPage from './lib/ReportPage.svelte';
  import StandardsPage from './lib/StandardsPage.svelte';

  const routes = [
    { hash: '#/register', label: '停机登记', component: RegisterPage },
    { hash: '#/segment', label: '分段确认', component: SegmentPage },
    { hash: '#/report', label: '班次报表', component: ReportPage },
    { hash: '#/standards', label: '节拍标准', component: StandardsPage }
  ];

  let current = window.location.hash || '#/register';

  onMount(() => {
    const onHash = () => {
      current = window.location.hash || '#/register';
      if (!routes.some(r => r.hash === current)) current = '#/register';
    };
    window.addEventListener('hashchange', onHash);
    onHash();
    return () => window.removeEventListener('hashchange', onHash);
  });

  $: activeRoute = routes.find(r => r.hash === current) || routes[0];
</script>

<header>
  <div class="brand">
    <span class="logo">停</span>
    <div>
      <h1>设备停机原因拆分与产能损失核算</h1>
      <p>同一时间轴分段确认 · 复核后统计 · 损失为节拍估算值</p>
    </div>
  </div>
  <nav>
    {#each routes as r}
      <a href={r.hash} class:active={activeRoute.hash === r.hash}>{r.label}</a>
    {/each}
  </nav>
</header>

<main>
  <svelte:component this={activeRoute.component} />
</main>

<footer>
  <span>报表中的产能损失为按标准节拍计算的估算值，不能当作实际减产数据。</span>
</footer>

<style>
  :global(body) {
    margin: 0;
    font-family: 'PingFang SC', 'Microsoft YaHei', 'Noto Sans CJK SC', system-ui, sans-serif;
    background: #f4f5f8;
    color: #222;
  }
  :global(.card) {
    background: #fff; border: 1px solid #e4e4ea; border-radius: 10px;
    padding: 14px 16px; margin-bottom: 14px;
  }
  :global(.muted) { color: #777; font-size: 13px; }
  :global(.hint) { color: #666; font-size: 13px; margin: 4px 0 14px; }
  :global(.alert) { padding: 9px 12px; border-radius: 8px; margin-bottom: 12px; font-size: 14px; }
  :global(.alert.error) { background: #fdecec; color: #b03a2e; border: 1px solid #f5c6c2; }
  :global(.alert.ok) { background: #e9f7ef; color: #1a6e3c; border: 1px solid #bfe6cd; }
  :global(.alert.warn) { background: #fdf6ec; color: #8a6d1a; border: 1px solid #f0e0c0; }
  :global(.badge) {
    display: inline-block; padding: 1px 8px; border-radius: 10px;
    font-size: 12px; background: #ececf1; color: #444;
  }
  :global(.badge.warn) { background: #fdf0d5; color: #8a6d1a; }
  :global(.badge.status-open) { background: #e8f0fe; color: #2f5fc4; }
  :global(.badge.status-pending_review) { background: #fdf0d5; color: #8a6d1a; }
  :global(.badge.status-reviewed) { background: #e9f7ef; color: #1a6e3c; }
  :global(.estimate-tag) {
    display: inline-block; padding: 0 7px; border-radius: 9px; font-size: 11px;
    background: #f5e6c8; color: #8a6d1a; border: 1px solid #e8d5ae; vertical-align: middle;
  }

  header {
    background: #1f2a44; color: #fff; padding: 14px 24px 0;
  }
  .brand { display: flex; gap: 12px; align-items: center; }
  .logo {
    width: 42px; height: 42px; border-radius: 10px; background: #2f6fed;
    display: flex; align-items: center; justify-content: center;
    font-size: 22px; font-weight: 700;
  }
  h1 { font-size: 18px; margin: 0; }
  .brand p { margin: 2px 0 0; font-size: 12px; color: #aab4d0; }
  nav { display: flex; gap: 4px; margin-top: 12px; }
  nav a {
    color: #c6cee4; text-decoration: none; padding: 8px 16px;
    border-radius: 8px 8px 0 0; font-size: 14px;
  }
  nav a.active { background: #f4f5f8; color: #1f2a44; font-weight: 600; }
  main { padding: 20px 24px; max-width: 1180px; margin: 0 auto; }
  :global(main h2) { font-size: 18px; margin: 0 0 4px; }
  :global(main h3) { font-size: 15px; margin: 0 0 8px; }
  footer {
    text-align: center; color: #999; font-size: 12px; padding: 18px;
  }
</style>
