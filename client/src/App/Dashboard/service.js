import {post} from 'modules/request';

const loadCount = async payload => {
  const response = await post('/api/workflow-instances/count', payload);
  const resJson = await response.json();
  return resJson.count;
};

export async function loadRunningInst() {
  return loadCount({
    running: true
  });
}

export async function loadInstWithoutIncidents() {
  return loadCount({
    running: true,
    withoutIncidents: true
  });
}

export async function loadInstWithIncidents() {
  return loadCount({
    running: true,
    withIncidents: true
  });
}
