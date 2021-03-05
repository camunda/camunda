/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {DEFAULT_FILTER} from 'modules/constants';
import {sanitizeFilter} from './sanitizeFilter';

describe('sanitizeFilter', () => {
  const groupedWorkflows = {
    bigVarProcess: {
      bpmnProcessId: 'bigVarProcess',
      name: 'Big variable process',
      workflows: [
        {
          id: '2251799813686288',
          name: 'Big variable process',
          version: 1,
          bpmnProcessId: 'bigVarProcess',
        },
      ],
    },
  };
  const baseFilter = {
    active: true,
    incidents: true,
    completed: true,
    canceled: true,
    startDate: '2020-01-01 12:30:00',
    errorMessage: 'error message',
    batchOperationId: 'batch-operation-id',
  };

  it('should return default filter if filter is not provided', () => {
    expect(sanitizeFilter(null)).toEqual(DEFAULT_FILTER);
    expect(sanitizeFilter(undefined)).toEqual(DEFAULT_FILTER);
  });

  it('should remove activityId, version and workflow if activityId is provided but workflow or version is not valid', () => {
    expect(
      sanitizeFilter(
        {
          ...baseFilter,
          activityId: 'activityId',
        },
        groupedWorkflows
      )
    ).toEqual(baseFilter);

    expect(
      sanitizeFilter(
        {
          ...baseFilter,
          activityId: 'activityId',
          workflow: 'some-workflow',
        },
        groupedWorkflows
      )
    ).toEqual(baseFilter);

    expect(
      sanitizeFilter(
        {
          ...baseFilter,
          activityId: 'activityId',
          version: '1',
        },
        groupedWorkflows
      )
    ).toEqual(baseFilter);

    expect(
      sanitizeFilter(
        {
          ...baseFilter,
          activityId: 'activityId',
          workflow: 'none-existing-workflow',
          version: '1',
        },
        groupedWorkflows
      )
    ).toEqual(baseFilter);
  });

  it('should remove activityId if version is all', () => {
    expect(
      sanitizeFilter(
        {
          ...baseFilter,
          activityId: 'activityId',
          workflow: 'bigVarProcess',
          version: 'all',
        },
        groupedWorkflows
      )
    ).toEqual({
      ...baseFilter,
      workflow: 'bigVarProcess',
      version: 'all',
    });
  });

  it('should remove activityId, workflow and version if workflow does not exist', () => {
    expect(
      sanitizeFilter(
        {
          ...baseFilter,
          activityId: 'activityId',
          workflow: 'bigVarProcess',
          version: '2',
        },
        groupedWorkflows
      )
    ).toEqual({
      ...baseFilter,
    });
  });

  it('should return original filters', () => {
    expect(
      sanitizeFilter(
        {
          ...baseFilter,
          activityId: 'activityId',
          workflow: 'bigVarProcess',
          version: '1',
        },
        groupedWorkflows
      )
    ).toEqual({
      ...baseFilter,
      activityId: 'activityId',
      workflow: 'bigVarProcess',
      version: '1',
    });
  });
});
