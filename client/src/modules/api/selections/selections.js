import {post} from 'modules/request';

const URL = '/api/workflow-instances';

export const batchRetry = async queries => {
  const url = `${URL}/operation`;
  const payload = {operationType: 'UPDATE_RETRIES', queries};

  const response = await post(url, payload);

  return await response.json();
};
