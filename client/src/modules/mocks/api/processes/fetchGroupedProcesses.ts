/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {mockGetRequest} from '../mockRequest';
import {ProcessDto} from 'modules/api/processes/fetchGroupedProcesses';

const mockFetchGroupedProcesses = () =>
  mockGetRequest<ProcessDto[]>('/api/processes/grouped');

export {mockFetchGroupedProcesses};
