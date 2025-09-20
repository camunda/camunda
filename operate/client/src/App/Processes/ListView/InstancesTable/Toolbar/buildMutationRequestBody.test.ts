/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {buildMutationRequestBody} from './buildMutationRequestBody';
import type {RequestFilters} from 'modules/utils/filter';
import type {
  CreateCancellationBatchOperationRequestBody,
  CreateIncidentResolutionBatchOperationRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.8';

type Body =
  | CreateIncidentResolutionBatchOperationRequestBody
  | CreateCancellationBatchOperationRequestBody;

describe('buildMutationRequestBody', () => {
  const baseFilter: RequestFilters = {
    activityId: 'taskA',
    incidents: true,
  };

  it('adds processInstanceKey.$in when includeIds present', () => {
    const body: Body = buildMutationRequestBody({
      baseFilter,
      includeIds: ['1', '2'],
      excludeIds: [],
    });

    expect(body).toEqual({
      filter: {
        elementId: 'taskA',
        hasIncident: true,
        processInstanceKey: {$in: ['1', '2']},
      },
    });
  });

  it('adds processInstanceKey.$notIn when excludeIds present', () => {
    const body: Body = buildMutationRequestBody({
      baseFilter,
      includeIds: [],
      excludeIds: ['3', '4'],
    });

    expect(body).toEqual({
      filter: {
        elementId: 'taskA',
        hasIncident: true,
        processInstanceKey: {$notIn: ['3', '4']},
      },
    });
  });

  it('combines includeIds and excludeIds into processInstanceKey', () => {
    const body: Body = buildMutationRequestBody({
      baseFilter,
      includeIds: ['1', '2'],
      excludeIds: ['3'],
    });

    expect(body).toEqual({
      filter: {
        elementId: 'taskA',
        hasIncident: true,
        processInstanceKey: {$in: ['1', '2'], $notIn: ['3']},
      },
    });
  });

  it('adds processDefinitionKey when provided', () => {
    const body: Body = buildMutationRequestBody({
      baseFilter,
      includeIds: [],
      excludeIds: [],
      processDefinitionKey: '2251799813693459',
    });

    expect(body).toEqual({
      filter: {
        elementId: 'taskA',
        hasIncident: true,
        processDefinitionKey: '2251799813693459',
      },
    });
  });

  it('handles single-element arrays', () => {
    const body: Body = buildMutationRequestBody({
      baseFilter,
      includeIds: ['only'],
      excludeIds: ['x'],
    });

    expect(body).toEqual({
      filter: {
        elementId: 'taskA',
        hasIncident: true,
        processInstanceKey: {$in: ['only'], $notIn: ['x']},
      },
    });
  });

  it('omits processInstanceKey when both include/exclude lists are empty', () => {
    const body: Body = buildMutationRequestBody({
      baseFilter,
      includeIds: [],
      excludeIds: [],
    });

    expect(body).toEqual({
      filter: {
        elementId: 'taskA',
        hasIncident: true,
      },
    });
  });

  it('uses OR combination when both incidents and active are selected', () => {
    const body: Body = buildMutationRequestBody({
      baseFilter: {
        activityId: 'taskA',
        incidents: true,
        active: true,
      },
      includeIds: [],
      excludeIds: [],
    });

    expect(body).toEqual({
      filter: {
        elementId: 'taskA',
        $or: [{hasIncident: true}, {state: {$eq: 'ACTIVE'}}],
      },
    });
  });

  it('uses hasIncident filter when only incidents checkbox is selected', () => {
    const body: Body = buildMutationRequestBody({
      baseFilter: {
        activityId: 'taskA',
        incidents: true,
        active: false,
      },
      includeIds: [],
      excludeIds: [],
    });

    expect(body).toEqual({
      filter: {
        elementId: 'taskA',
        hasIncident: true,
      },
    });
  });

  it('uses state filter when only active checkbox is selected', () => {
    const body: Body = buildMutationRequestBody({
      baseFilter: {
        activityId: 'taskA',
        incidents: false,
        active: true,
      },
      includeIds: [],
      excludeIds: [],
    });

    expect(body).toEqual({
      filter: {
        elementId: 'taskA',
        state: {$eq: 'ACTIVE'},
      },
    });
  });
});
