import {get} from 'request';

export async function getReportData(id) {
  let response;

  try {
    response = await get(`/api/share/report/${id}/evaluate`);
  } catch(e) {
    return null;
  }

  return await response.json();
}