import {get, del, put, post} from 'request';

export async function loadSingleReport(id) {
  const response = await get('/api/report/' + id);

  return await response.json();
}

export async function remove(id) {
  return await del(`/api/report/${id}`);
}

export async function loadProcessDefinitionXml(processDefinitionKey, processDefinitionVersion) {
  const response = await get('/api/process-definition/xml', {
    processDefinitionKey,
    processDefinitionVersion
  });

  return await response.text();
}

export async function getReportData(query) {
  let response;

  try {
    if (typeof query !== 'object') {
      // evaluate saved report
      response = await get(`/api/report/${query}/evaluate`);
    } else {
      // evaluate unsaved report
      response = await post('/api/report/evaluate', query);
    }
  } catch (e) {
    return null;
  }

  return await response.json();
}

export async function saveReport(id, data) {
  return await put(`/api/report/${id}`, data);
}

export async function shareReport(reportId) {
  const body = {
    reportId
  };
  const response = await post(`/api/share/report`, body);

  const json = await response.json();
  return json.id;
}

export async function getSharedReport(reportId) {
  const response = await get(`/api/share/report/${reportId}`);

  if (response.status > 201) {
    return '';
  } else {
    const json = await response.json();
    return json.id;
  }
}

export async function revokeReportSharing(id) {
  return await del(`/api/share/report/${id}`);
}

export const isRawDataReport = (report, data) => {
  return (
    data &&
    data.view &&
    data.view.operation === 'rawData' &&
    report &&
    report.result &&
    report.result[0]
  );
};
