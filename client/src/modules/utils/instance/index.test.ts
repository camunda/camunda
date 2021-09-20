/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createInstance} from 'modules/testUtils';
import {STATE} from 'modules/constants';

import * as instanceUtils from './index';
import {isWithIncident, isRunning} from 'modules/utils/instance';

const mockIncidentInstance = {
  id: '8590375632-2',
  state: 'INCIDENT',
};

const mockActiveInstance = {
  id: '8590375632-2',
  state: 'ACTIVE',
};

describe('instance utils', () => {
  describe('isWithIncident', () => {
    it('should return true if an instance has an incident', () => {
      expect(isWithIncident(mockIncidentInstance)).toBe(true);
    });

    it('should return false if an instance is active', () => {
      expect(isWithIncident(mockActiveInstance)).toBe(false);
    });
  });

  describe('isRunning', () => {
    const mockCompletedInstance = {
      id: '8590375632-2',
      state: STATE.COMPLETED,
    };
    const mockCanceldInstance = {
      id: '8590375632-2',
      state: STATE.CANCELED,
    };

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
      const instance = createInstance({processName: null});
      const instanceName = instanceUtils.getProcessName(instance);

      // then
      expect(instanceName).toBe(instance.bpmnProcessId);
    });
  });
});
