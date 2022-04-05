/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {createInstance} from 'modules/testUtils';

import * as instanceUtils from './index';
import {hasIncident, isRunning} from 'modules/utils/instance';

const mockIncidentInstance = {
  id: '8590375632-2',
  state: 'INCIDENT',
} as const;

const mockActiveInstance = {
  id: '8590375632-2',
  state: 'ACTIVE',
} as const;

describe('instance utils', () => {
  describe('hasIncident', () => {
    it('should return true if an instance has an incident', () => {
      expect(hasIncident(mockIncidentInstance)).toBe(true);
    });

    it('should return false if an instance is active', () => {
      expect(hasIncident(mockActiveInstance)).toBe(false);
    });
  });

  describe('isRunning', () => {
    const mockCompletedInstance = {
      id: '8590375632-2',
      state: 'COMPLETED',
    } as const;
    const mockCanceldInstance = {
      id: '8590375632-2',
      state: 'CANCELED',
    } as const;

    it('should return true if an instance is running', () => {
      expect(isRunning(mockIncidentInstance)).toBe(true);
      expect(isRunning(mockActiveInstance)).toBe(true);

      expect(isRunning(mockCompletedInstance)).toBe(false);
      expect(isRunning(mockCanceldInstance)).toBe(false);
    });
  });

  describe('getProcessName', () => {
    it('should return processName when it exists', () => {
      // given
      const instance = createInstance();
      const instanceName = instanceUtils.getProcessName(instance);

      // then
      expect(instanceName).toBe(instance.processName);
    });

    it('should fallback to bpmnProcessId', () => {
      // given
      const instance = createInstance({processName: undefined});
      const instanceName = instanceUtils.getProcessName(instance);

      // then
      expect(instanceName).toBe(instance.bpmnProcessId);
    });
  });
});
