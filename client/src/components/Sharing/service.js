import {get} from 'request';

export async function evaluateEntity(id, type) {
  let response;

  try {
    response = await get(`/api/share/${type}/${id}/evaluate`);
  } catch (e) {
    return null;
  }

  return await response.json();
}

export async function loadReport(report) {
  try {
    const response = await get(`/api/share/report/${report.shareId}/evaluate`);
    const sharedReport = await response.json();
    return sharedReport.report;
  } catch (error) {
    return await error.json();
  }
}
