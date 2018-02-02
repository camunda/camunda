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

export async function getFlowNodeNames(id) {
  const response = await post(`/api/process-definition/${id}/flowNodeNames`, []);

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
