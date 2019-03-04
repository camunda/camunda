/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createInstance} from 'modules/testUtils';
import {STATE} from 'modules/constants';

import * as instanceUtils from './instance';
import {xTimes, createOperation} from 'modules/testUtils';

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
      activityId: 'taskA'
    }
  ]
};

describe('instance utils', () => {
  describe('getActiveIncident', () => {
    it('should return null if there is no incident', () => {
      expect(instanceUtils.getActiveIncident([])).toBe(null);
    });

    it('should return an object if an instance has incidents', () => {
      expect(
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
    let mockOperations;

    const createMockOperations = (amount, array) =>
      xTimes(amount)(counter => {
        array.push(
          createOperation({startDate: `2018-10-1${counter}T09:20:38.661Z`})
        );
      });

    it('should return {} when no operations are available', () => {
      mockOperations = [];
      createMockOperations(0, mockOperations);
      expect(instanceUtils.getLatestOperation(mockOperations)).toEqual({});
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
