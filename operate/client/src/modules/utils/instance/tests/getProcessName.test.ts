/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
