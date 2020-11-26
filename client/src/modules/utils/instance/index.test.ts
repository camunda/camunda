/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createInstance} from 'modules/testUtils';
import {STATE} from 'modules/constants';

import * as instanceUtils from './index';
import {xTimes, createOperation} from 'modules/testUtils';
import {isWithIncident, isRunning} from 'modules/utils/instance';

const {ACTIVE} = STATE;

const activeWithIncidents = {
  state: ACTIVE,
  incidents: [
    {
      id: '4295776400',
      errorType: 'IO_MAPPING_ERROR',
      errorMessage:
        'Could not apply output mappings: Task was completed without payload',
      state: ACTIVE,
      activityId: 'taskA',
    },
  ],
};

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

  describe('getActiveIncident', () => {
    it('should return null if there is no incident', () => {
      expect(instanceUtils.getActiveIncident([])).toBe(null);
    });

    it('should return an object if an instance has incidents', () => {
      expect(
        // @ts-expect-error ts-migrate(2345) FIXME: Type '{ id: string; errorType: string; errorMessag... Remove this comment to see the full error message
        instanceUtils.getActiveIncident(activeWithIncidents.incidents)
      ).toBe(activeWithIncidents.incidents[0]);
    });
  });

  describe('getWorkflowName', () => {
    it('should return workflowName when it exists', () => {
      // given
      const instance = createInstance();
      const instanceName = instanceUtils.getWorkflowName(instance);

      // then
      expect(instanceName).toBe(instance.workflowName);
    });

    it('should fallback to bpmnProcessId', () => {
      // given
      const instance = createInstance({workflowName: null});
      const instanceName = instanceUtils.getWorkflowName(instance);

      // then
      expect(instanceName).toBe(instance.bpmnProcessId);
    });
  });

  describe('getLatestOperation', () => {
    let mockOperations: any;

    const createMockOperations = (amount: any, array: any) =>
      xTimes(amount)((counter: any) => {
        array.push(
          createOperation({startDate: `2018-10-1${counter}T09:20:38.661Z`})
        );
      });

    it('should return null when no operations are available', () => {
      mockOperations = [];
      createMockOperations(0, mockOperations);
      expect(instanceUtils.getLatestOperation(mockOperations)).toEqual(null);
    });

    it('should retrun operations sorted in ascending order by startDate', () => {
      // Create Mock Data
      mockOperations = [];
      createMockOperations(3, mockOperations);
      const latestDate = `2018-10-13T09:20:38.661Z`;

      expect(instanceUtils.getLatestOperation(mockOperations)).toEqual(
        createOperation({startDate: latestDate})
      );
    });
  });
});
