/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {mockGetRequest} from '../mockRequest';
import {DecisionInstanceDto} from 'modules/api/decisionInstances/fetchDecisionInstance';

const mockFetchDecisionInstance = () =>
  mockGetRequest<DecisionInstanceDto>(
    '/api/decision-instances/:decisionInstanceId'
  );

export {mockFetchDecisionInstance};
