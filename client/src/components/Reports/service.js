import {get, del, put} from 'request';

export async function loadSingleReport(id) {
  const response = await get('/api/report/' + id);

  return await response.json();
}

export async function remove(id) {
  return await del(`/api/report/${id}`);
}

export async function loadProcessDefinitions() {
  const response = await get('/api/process-definition');

  return await response.json();
}

export async function getReportData(query) {
  // let response;
  // if(typeof query !== 'object') {
  //   // evaluate saved report
  //   response = await get(`/api/report/${query}/evaluate`);
  // } else {
  //   // evaluate unsaved report
  //   response = await post('/api/report/evaluate', query);
  // }

  // return await response.json();

  //TODO: use code above once backend implements evaluate query
  return {
    processDefinitionId: 'asdf',
    filter: null,
    view: {
      operation: 'count',
      entity: 'processInstance'
    },
    groupBy: {
      type: 'startDate', // 'startDate', 'flowNodes'
      unit: 'day' // month, hour, day, year, week, null
    },
    visualization: 'json',
    result: {
      "2017-09-01": 23,
      "2017-09-02": 7,
      "2017-09-03": 11
    }
  };
}

export async function saveReport(id, data) {
  return await put(`/api/report/${id}`, data);
}
