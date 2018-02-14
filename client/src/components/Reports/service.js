import {get, del, put, post} from 'request';

export async function loadSingleReport(id) {
  const response = await get('/api/report/' + id);

  return await response.json();
}

export async function remove(id) {
  return await del(`/api/report/${id}`);
}

export async function loadProcessDefinitions() {
  const response = await get('/api/process-definition/groupedByKey');

  return await response.json();
}

export async function loadProcessDefinitionXml(id) {
  const response = await get('/api/process-definition/xml', {ids: [id]});

  return (await response.json())[id];
}

export async function getFlowNodeNames(id) {
  const response = await post(`/api/flow-node/${id}/flowNodeNames`, []);

  const json = await response.json();

  return await json.flowNodeNames;
}

export async function getReportData(query) {
  let response;

  try {
    if(typeof query !== 'object') {
      // evaluate saved report
      response = await get(`/api/report/${query}/evaluate`);
    } else {
      // evaluate unsaved report
      response = await post('/api/report/evaluate', query);
    }
  } catch(e) {
    return null;
  }

  return await response.json();
}

export async function saveReport(id, data) {
  return await put(`/api/report/${id}`, data);
}

export async function shareReport(reportId) {
  const body = {
    reportId,
    type: 'REPORT'
  };
  const response = await post(`/api/share/report`, body);

  const json = await response.json();
  return json.id;
}

export async function getSharedReport(reportId) {
  const response = await get(`/api/share/report/${reportId}`);

  if(response.status > 201) {
    return '';
  } else {
    const json = await response.json();
    return json.id;
  }
}

export async function revokeReportSharing(id) {
  return await del(`/api/share/report/${id}`);
}
