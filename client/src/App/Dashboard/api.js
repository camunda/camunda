import {post} from 'modules/request';

const URL = '/api/workflow-instances/count';

const subSets = {
  active: {
    withoutIncidents: true
  },
  incidents: {
    withIncidents: true
  }
};

const request = async payload => {
  const response = await post(URL, Object.assign({running: true}, payload));
  const resJson = await response.json();
  return resJson.count;
};

export const fetchInstancesCount = type => request(subSets[type]);
