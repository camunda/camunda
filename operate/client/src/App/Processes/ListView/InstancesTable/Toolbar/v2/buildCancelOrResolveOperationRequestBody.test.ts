/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {buildCancelOrResolveOperationRequestBody} from './buildCancelOrResolveOperationRequestBody';
import type {RequestFilters} from 'modules/utils/filter';
import type {
  CreateCancellationBatchOperationRequestBody,
  CreateIncidentResolutionBatchOperationRequestBody,
} from '@vzeta/camunda-api-zod-schemas/8.8';

type Body =
  | CreateIncidentResolutionBatchOperationRequestBody
  | CreateCancellationBatchOperationRequestBody;

describe('buildCancelOrResolveOperationRequestBody', () => {
  const baseFilter: RequestFilters = {
    activityId: 'taskA',
    incidents: true,
  };

  it('adds processInstanceKey.$in when includeIds present', () => {
    const body: Body = buildCancelOrResolveOperationRequestBody(
      baseFilter,
      ['1', '2'],
      [],
      undefined,
    );

    expect(body).toEqual({
      elementId: 'taskA',
      hasIncident: true,
      processInstanceKey: {$in: ['1', '2']},
    });
  });

  it('adds processInstanceKey.$notIn when excludeIds present', () => {
    const body: Body = buildCancelOrResolveOperationRequestBody(
      baseFilter,
      [],
      ['3', '4'],
      undefined,
    );

    expect(body).toEqual({
      elementId: 'taskA',
      hasIncident: true,
      processInstanceKey: {$notIn: ['3', '4']},
    });
  });

  it('combines includeIds and excludeIds into processInstanceKey', () => {
    const body: Body = buildCancelOrResolveOperationRequestBody(
      baseFilter,
      ['1', '2'],
      ['3'],
      undefined,
    );

    expect(body).toEqual({
      elementId: 'taskA',
      hasIncident: true,
      processInstanceKey: {$in: ['1', '2'], $notIn: ['3']},
    });
  });

  it('adds processDefinitionKey when provided', () => {
    const body: Body = buildCancelOrResolveOperationRequestBody(
      baseFilter,
      [],
      [],
      '2251799813693459',
    );

    expect(body).toEqual({
      elementId: 'taskA',
      hasIncident: true,
      processDefinitionKey: {$eq: '2251799813693459'},
    });
  });

  it('handles single-element arrays', () => {
    const body: Body = buildCancelOrResolveOperationRequestBody(
      baseFilter,
      ['only'],
      ['x'],
      undefined,
    );

    expect(body).toEqual({
      elementId: 'taskA',
      hasIncident: true,
      processInstanceKey: {$in: ['only'], $notIn: ['x']},
    });
  });

  it('omits processInstanceKey when both include/exclude lists are empty', () => {
    const body: Body = buildCancelOrResolveOperationRequestBody(
      baseFilter,
      [],
      [],
      null,
    );

    expect(body).toEqual({
      elementId: 'taskA',
      hasIncident: true,
    });
  });
});
