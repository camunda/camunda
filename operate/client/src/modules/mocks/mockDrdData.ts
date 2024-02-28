/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {DrdDataDto} from 'modules/api/decisionInstances/fetchDrdData';

const mockDrdData: DrdDataDto = {
  'invoice-assign-approver': [{decisionInstanceId: '0', state: 'EVALUATED'}],
  invoiceClassification: [
    {
      decisionInstanceId: '1',
      state: 'FAILED',
    },
  ],
  'calc-key-figures': [
    {
      decisionInstanceId: '2',
      state: 'EVALUATED',
    },
  ],
};

export {mockDrdData};
