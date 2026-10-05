/** 后端 API 封装 */
async function request(path, options = {}) {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...options
  });
  if (!res.ok) {
    let message = `请求失败 (${res.status})`;
    try {
      const body = await res.json();
      if (body.message) message = body.message;
    } catch { /* 保留默认消息 */ }
    throw new Error(message);
  }
  return res.json();
}

export const api = {
  listEquipment: () => request('/api/equipment'),
  listEvents: (params = {}) => {
    const q = new URLSearchParams(Object.entries(params).filter(([, v]) => v)).toString();
    return request('/api/events' + (q ? `?${q}` : ''));
  },
  getEvent: (id) => request(`/api/events/${id}`),
  createEvent: (data) => request('/api/events', { method: 'POST', body: JSON.stringify(data) }),
  updateTimes: (id, data) => request(`/api/events/${id}/times`, { method: 'PUT', body: JSON.stringify(data) }),
  saveSegments: (id, segments) => request(`/api/events/${id}/segments`, { method: 'PUT', body: JSON.stringify({ segments }) }),
  review: (id, reviewerName) => request(`/api/events/${id}/review`, { method: 'POST', body: JSON.stringify({ reviewerName }) }),
  reopen: (id) => request(`/api/events/${id}/reopen`, { method: 'POST', body: '{}' }),
  recalculate: (id) => request(`/api/events/${id}/recalculate`, { method: 'POST', body: '{}' }),
  calculations: (id) => request(`/api/events/${id}/calculations`),
  listStandards: (equipmentId) => request('/api/standards' + (equipmentId ? `?equipmentId=${equipmentId}` : '')),
  createStandard: (data) => request('/api/standards', { method: 'POST', body: JSON.stringify(data) }),
  shiftReport: (date, shift) => request(`/api/reports/shift?date=${date}&shift=${shift}`),
  mockProduction: (date, shift) => request(`/api/mock/production?date=${date}&shift=${shift}`)
};
