/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {requestAndParse} from 'modules/request';

const fetchBatchOperations = async ({
  pageSize,
  searchAfter,
}: {
  pageSize: number;
  searchAfter?: OperationEntity['sortValues'];
}) => {
  return requestAndParse<OperationEntity[]>({
    url: '/api/batch-operations',
    method: 'POST',
    body: {
      pageSize,
      searchAfter,
    },
  });
};

export {fetchBatchOperations};
