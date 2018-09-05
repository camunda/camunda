import {post} from 'modules/request';
import {BATCH_OPERATION_TYPE} from 'modules/constants';

const URL = '/api/workflow-instances';

export const batchRetry = async queries => {
  const url = `${URL}/operation`;
  const payload = {operationType: BATCH_OPERATION_TYPE.UPDATE_RETRIES, queries};

  await post(url, payload);
};
