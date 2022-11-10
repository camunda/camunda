/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {mockGetRequest} from '../mockRequest';
import {SequenceFlowsDto} from 'modules/api/processInstances/sequenceFlows';

const mockFetchSequenceFlows = (contextPath = '') =>
  mockGetRequest<SequenceFlowsDto>(
    `${contextPath}/api/process-instances/:processInstanceId/sequence-flows`
  );

export {mockFetchSequenceFlows};
