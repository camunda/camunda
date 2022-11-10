/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {DecisionDto} from 'modules/api/decisions/fetchGroupedDecisions';
import {mockGetRequest} from '../mockRequest';

const mockFetchGroupedDecisions = () =>
  mockGetRequest<DecisionDto[]>('/api/decisions/grouped');

export {mockFetchGroupedDecisions};
