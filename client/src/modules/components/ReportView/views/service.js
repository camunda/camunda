import {get} from 'request';

export async function getCamundaEndpoints() {
  const response = await get('/api/camunda');
  return await response.json();
}

export function getRelativeValue(data, total) {
  return Math.round(data / total * 1000) / 10 + '%';
}

const units = {
  years: 1000 * 60 * 60 * 24 * 30 * 12,
  months: 1000 * 60 * 60 * 24 * 30,
  weeks: 1000 * 60 * 60 * 24 * 7,
  days: 1000 * 60 * 60 * 24,
  hours: 1000 * 60 * 60,
  minutes: 1000 * 60,
  seconds: 1000,
  millis: 1
};

export function convertToMilliseconds(value, unit) {
  return value * units[unit];
}
