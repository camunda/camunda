/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {createMultiInstanceFlowNodeInstances} from 'modules/testUtils';

const PROCESS_INSTANCE_ID = 'processInstance';
const mockFlowNodeInstances =
  createMultiInstanceFlowNodeInstances(PROCESS_INSTANCE_ID);

export {PROCESS_INSTANCE_ID, mockFlowNodeInstances};
