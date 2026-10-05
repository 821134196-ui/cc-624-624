// 极简 API 封装,统一错误提示
async function request(method, url, body) {
  const opt = { method, headers: {} };
  if (body !== undefined) {
    opt.headers['Content-Type'] = 'application/json';
    opt.body = JSON.stringify(body);
  }
  const res = await fetch(url, opt);
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    const msg = data?.message || `请求失败 ${res.status}`;
    const err = new Error(msg);
    err.status = res.status;
    err.data = data;
    throw err;
  }
  return data;
}

export const api = {
  get: (u) => request('GET', u),
  post: (u, b) => request('POST', u, b ?? {}),
  del: (u) => request('DELETE', u),

  // 设备
  equipment: () => request('GET', '/api/equipment'),

  // 事件
  events: () => request('GET', '/api/events'),
  event: (id) => request('GET', `/api/events/${id}`),
  register: (b) => request('POST', '/api/events', b),
  resolve: (id, b) => request('POST', `/api/events/${id}/resolve`, b),
  saveSegments: (id, b) => request('POST', `/api/events/${id}/segments`, b),
  conservation: (id) => request('GET', `/api/events/${id}/conservation`),
  continuity: (id) => request('GET', `/api/events/${id}/continuity`),
  review: (id, b) => request('POST', `/api/events/${id}/review`, b),
  reject: (id, b) => request('POST', `/api/events/${id}/reject`, b),
  delete: (id) => request('DELETE', `/api/events/${id}`),

  // 核算
  calculations: (id) => request('GET', `/api/events/${id}/calculations`),
  recalc: (id, b) => request('POST', `/api/events/${id}/recalculate`, b),

  // 标准
  standards: (equipId) => request('GET', `/api/equipment/${equipId}/standards`),
  publishStandard: (equipId, b) => request('POST', `/api/equipment/${equipId}/standards`, b),

  // 报表 / 模拟产量
  report: (date, shift) => {
    const p = new URLSearchParams();
    if (date) p.set('date', date);
    if (shift) p.set('shift', shift);
    return request('GET', `/api/reports/shift?${p}`);
  },
  backfill: (equipId, from, to) =>
    request('POST', `/api/equipment/${equipId}/countback/backfill?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`),
  countback: (equipId, from, to) =>
    request('GET', `/api/equipment/${equipId}/countback?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`)
};
