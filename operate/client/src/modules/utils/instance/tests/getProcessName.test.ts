/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {createInstance} from 'modules/testUtils';
import {getProcessName} from '..';

describe('getProcessName', () => {
  it('should return processName when it exists', () => {
    const instance = createInstance();
    const instanceName = getProcessName(instance);

    expect(instanceName).toBe(instance.processName);
  });

  it('should fallback to bpmnProcessId', () => {
    const instance = createInstance({processName: undefined});
    const instanceName = getProcessName(instance);

    expect(instanceName).toBe(instance.bpmnProcessId);
  });
});
