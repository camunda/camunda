/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
