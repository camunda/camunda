/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {mockGetRequest} from '../mockRequest';
import {DrdDataDto} from 'modules/api/decisionInstances/fetchDrdData';

const mockFetchDrdData = () =>
  mockGetRequest<DrdDataDto>(
    '/api/decision-instances/:decisionInstanceId/drd-data'
  );

export {mockFetchDrdData};
