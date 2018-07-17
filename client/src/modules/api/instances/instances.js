import {post, get} from 'modules/request';

const URL = '/api/workflow-instances';

export async function fetchWorkflowInstance(id) {
  const response = await get(`${URL}/${id}`);
  return await response.json();
}

export async function fetchWorkflowInstances(options) {
  const {filter, firstResult, maxResults} = options;

  const url = `${URL}?firstResult=${firstResult}&maxResults=${maxResults}`;
  const response = await post(url, filter);

  return await response.json();
}

export async function fetchWorkflowInstancesCount(payload) {
  const response = await post(`${URL}/count`, payload);
  const resJson = await response.json();
  return resJson.count;
}

export async function batchRety() {
  // TODO: TBD
  // const body = { instances }
  //  await post('/api/selection/...', body);
}

export async function fetchWorkflowInstanceBySelection(payload) {
  //TODO: replace mock data;
  const demoInstance = {
    id: '4294984040',
    workflowId: '1',
    startDate: '2018-07-10T08:58:58.073+0000',
    endDate: null,
    state: 'ACTIVE',
    businessKey: 'demoProcess',
    incidents: [
      {
        id: '4295665536',
        errorType: 'IO_MAPPING_ERROR',
        errorMessage: 'No data found for query $.foo.',
        state: 'ACTIVE',
        activityId: 'taskA',
        activityInstanceId: '4294984912',
        jobId: null
      }
    ],
    activities: []
  };

  const times = x => f => {
    if (x > 0) {
      f();
      times(x - 1)(f);
    }
  };

  const workfowInstances = [];
  times(10)(() => workfowInstances.push(demoInstance));

  return {workfowInstances, totalCount: 145};
}
