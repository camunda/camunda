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

export function createLoadReportCallback(dashboardShareId) {
  return async report => {
    try {
      const response = await get(
        `/api/share/dashboard/${dashboardShareId}/report/${report.id}/evaluate`
      );
      return await response.json();
    } catch (error) {
      return await error.json();
    }
  };
}
