import {get, post, del, put} from 'request';

export async function loadAlerts() {
  const response = await get('/api/alert');

  return await response.json();
}

export async function loadReports() {
  const response = await get('/api/report');

  return await response.json();
}

export async function saveNewAlert(alert) {
  const response = await post('/api/alert', alert);

  const json = await response.json();

  return json.id;
}

export async function updateAlert(id, alert) {
  return await put('/api/alert/' + id, alert);
}

export async function deleteAlert(id) {
  return await del('api/alert/' + id);
}

export async function emailNotificationIsEnabled() {
  const response = await get('/api/alert/email/isEnabled');

  return (await response.json()).enabled;
}

const timeUnits = {
  milliseconds: 1,
  seconds: 1000,
  minutes: 60 * 1000,
  hours: 60 * 60 * 1000,
  days: 24 * 60 * 60 * 1000,
  weeks: 7 * 24 * 60 * 60 * 1000,
  months: 30 * 24 * 60 * 60 * 1000
};

export const convertDurationToObject = value => {
  // sort the time units in descending order, then find the first one
  // that fits the provided value without any decimal places
  const [divisor, unit] = Object.keys(timeUnits)
    .map(key => [timeUnits[key], key])
    .sort(([a], [b]) => b - a)
    .find(([divisor]) => ~~(value / divisor) === value / divisor);

  return {
    value: (value / divisor).toString(),
    unit
  };
};

export const convertDurationToSingleNumber = threshold => {
  if (typeof threshold.value === 'undefined') {
    return threshold;
  }
  return threshold.value * timeUnits[threshold.unit];
};
