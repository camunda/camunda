import {post} from 'modules/request';

const URL = '/api/workflow-instances';

export const batchRetry = async queries => {
  const url = `${URL}/operation`;
  const payload = {operationType: 'UPDATE_RETRIES', queries};

  await post(url, payload);
};
