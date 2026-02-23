/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {buildMutationRequestBody} from './buildMutationRequestBody';
import type {
  CreateCancellationBatchOperationRequestBody,
  CreateIncidentResolutionBatchOperationRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.8';

type Body =
  | CreateIncidentResolutionBatchOperationRequestBody
  | CreateCancellationBatchOperationRequestBody;

describe('buildMutationRequestBody', () => {
  const createSearchParams = (params: Record<string, string>) => {
    return new URLSearchParams(params);
  };

  it('adds processInstanceKey.$in when includeIds present', () => {
    const searchParams = createSearchParams({
      flowNodeId: 'taskA',
      incidents: 'true',
    });

    const body: Body = buildMutationRequestBody({
      searchParams,
      includeIds: ['1', '2'],
      excludeIds: [],
    });

    expect(body).toEqual({
      filter: {
        elementId: {$eq: 'taskA'},
        elementInstanceState: {$eq: 'ACTIVE'},
        hasIncident: true,
        processInstanceKey: {$in: ['1', '2']},
      },
    });
  });

  it('adds processInstanceKey.$notIn when excludeIds present', () => {
    const searchParams = createSearchParams({
      flowNodeId: 'taskA',
      incidents: 'true',
    });

    const body: Body = buildMutationRequestBody({
      searchParams,
      includeIds: [],
      excludeIds: ['3', '4'],
    });

    expect(body).toEqual({
      filter: {
        elementId: {$eq: 'taskA'},
        elementInstanceState: {$eq: 'ACTIVE'},
        hasIncident: true,
        processInstanceKey: {$notIn: ['3', '4']},
      },
    });
  });

  it('combines includeIds and excludeIds into processInstanceKey', () => {
    const searchParams = createSearchParams({
      flowNodeId: 'taskA',
      incidents: 'true',
    });

    const body: Body = buildMutationRequestBody({
      searchParams,
      includeIds: ['1', '2'],
      excludeIds: ['3'],
    });

    expect(body).toEqual({
      filter: {
        elementId: {$eq: 'taskA'},
        elementInstanceState: {$eq: 'ACTIVE'},
        hasIncident: true,
        processInstanceKey: {$in: ['1', '2'], $notIn: ['3']},
      },
    });
  });

  it('handles single-element arrays', () => {
    const searchParams = createSearchParams({
      flowNodeId: 'taskA',
      incidents: 'true',
    });

    const body: Body = buildMutationRequestBody({
      searchParams,
      includeIds: ['only'],
      excludeIds: ['x'],
    });

    expect(body).toEqual({
      filter: {
        elementId: {$eq: 'taskA'},
        elementInstanceState: {$eq: 'ACTIVE'},
        hasIncident: true,
        processInstanceKey: {$in: ['only'], $notIn: ['x']},
      },
    });
  });

  it('omits processInstanceKey when both include/exclude lists are empty', () => {
    const searchParams = createSearchParams({
      flowNodeId: 'taskA',
      incidents: 'true',
    });

    const body: Body = buildMutationRequestBody({
      searchParams,
      includeIds: [],
      excludeIds: [],
    });

    expect(body).toEqual({
      filter: {
        elementId: {$eq: 'taskA'},
        elementInstanceState: {$eq: 'ACTIVE'},
        hasIncident: true,
      },
    });
  });

  it('uses OR combination when both incidents and active are selected', () => {
    const searchParams = createSearchParams({
      flowNodeId: 'taskA',
      incidents: 'true',
      active: 'true',
    });

    const body: Body = buildMutationRequestBody({
      searchParams,
      includeIds: [],
      excludeIds: [],
    });

    expect(body).toEqual({
      filter: {
        elementId: {$eq: 'taskA'},
        elementInstanceState: {$eq: 'ACTIVE'},
        $or: [{state: {$in: ['ACTIVE']}}, {hasIncident: true}],
      },
    });
  });

  it('uses hasIncident filter when only incidents checkbox is selected', () => {
    const searchParams = createSearchParams({
      flowNodeId: 'taskA',
      incidents: 'true',
    });

    const body: Body = buildMutationRequestBody({
      searchParams,
      includeIds: [],
      excludeIds: [],
    });

    expect(body).toEqual({
      filter: {
        elementId: {$eq: 'taskA'},
        elementInstanceState: {$eq: 'ACTIVE'},
        hasIncident: true,
      },
    });
  });

  it('uses state filter when only active checkbox is selected', () => {
    const searchParams = createSearchParams({
      flowNodeId: 'taskA',
      active: 'true',
    });

    const body: Body = buildMutationRequestBody({
      searchParams,
      includeIds: [],
      excludeIds: [],
    });

    expect(body).toEqual({
      filter: {
        elementId: {$eq: 'taskA'},
        elementInstanceState: {$eq: 'ACTIVE'},
        state: {$eq: 'ACTIVE'},
        hasIncident: false,
      },
    });
  });

  it('maps additional filter fields to request body', () => {
    const searchParams = createSearchParams({
      flowNodeId: 'taskA',
      errorMessage: 'some error',
      tenant: 'tenant-xyz',
      operationId: 'batch-123',
      parentInstanceId: 'parent-456',
      retriesLeft: 'true',
      incidentErrorHashCode: '37136123613781',
      active: 'true',
    });

    const body: Body = buildMutationRequestBody({
      searchParams,
      includeIds: [],
      excludeIds: [],
    });

    expect(body).toEqual({
      filter: {
        elementId: {$eq: 'taskA'},
        elementInstanceState: {$eq: 'ACTIVE'},
        errorMessage: {$in: ['some error']},
        tenantId: {$eq: 'tenant-xyz'},
        batchOperationId: {$eq: 'batch-123'},
        parentProcessInstanceKey: {$eq: 'parent-456'},
        hasRetriesLeft: true,
        incidentErrorHashCode: 37136123613781,
        state: {$eq: 'ACTIVE'},
        hasIncident: false,
      },
    });
  });

  it('maps process to processDefinitionId', () => {
    const searchParams = createSearchParams({
      flowNodeId: 'taskA',
      process: 'orderProcess',
      active: 'true',
    });

    const body: Body = buildMutationRequestBody({
      searchParams,
      includeIds: [],
      excludeIds: [],
    });

    expect(body).toEqual({
      filter: {
        elementId: {$eq: 'taskA'},
        elementInstanceState: {$eq: 'ACTIVE'},
        processDefinitionId: {$eq: 'orderProcess'},
        state: {$eq: 'ACTIVE'},
        hasIncident: false,
      },
    });
  });

  it('maps startDateAfter / startDateBefore to startDate $gt/$lt', () => {
    const after = '2020-01-01T00:00:00Z';
    const before = '2020-01-02T00:00:00Z';

    expect(
      buildMutationRequestBody({
        searchParams: createSearchParams({
          startDateAfter: after,
          startDateBefore: before,
          active: 'true',
        }),
        includeIds: [],
        excludeIds: [],
      }),
    ).toEqual({
      filter: {
        startDate: {
          $gt: '2020-01-01T00:00:00.000Z',
          $lt: '2020-01-02T00:00:00.000Z',
        },
        state: {$eq: 'ACTIVE'},
        hasIncident: false,
      },
    });

    expect(
      buildMutationRequestBody({
        searchParams: createSearchParams({
          startDateAfter: after,
          active: 'true',
        }),
        includeIds: [],
        excludeIds: [],
      }),
    ).toEqual({
      filter: {
        startDate: {
          $gt: '2020-01-01T00:00:00.000Z',
        },
        state: {$eq: 'ACTIVE'},
        hasIncident: false,
      },
    });

    expect(
      buildMutationRequestBody({
        searchParams: createSearchParams({
          startDateBefore: before,
          active: 'true',
        }),
        includeIds: [],
        excludeIds: [],
      }),
    ).toEqual({
      filter: {
        startDate: {
          $lt: '2020-01-02T00:00:00.000Z',
        },
        state: {$eq: 'ACTIVE'},
        hasIncident: false,
      },
    });
  });

  it('maps endDateAfter / endDateBefore to endDate $gt/$lt', () => {
    const after = '2020-01-01T00:00:00Z';
    const before = '2020-01-02T00:00:00Z';

    expect(
      buildMutationRequestBody({
        searchParams: createSearchParams({
          endDateAfter: after,
          endDateBefore: before,
          active: 'true',
        }),
        includeIds: [],
        excludeIds: [],
      }),
    ).toEqual({
      filter: {
        endDate: {
          $gt: '2020-01-01T00:00:00.000Z',
          $lt: '2020-01-02T00:00:00.000Z',
        },
        state: {$eq: 'ACTIVE'},
        hasIncident: false,
      },
    });

    expect(
      buildMutationRequestBody({
        searchParams: createSearchParams({
          endDateAfter: after,
          active: 'true',
        }),
        includeIds: [],
        excludeIds: [],
      }),
    ).toEqual({
      filter: {
        endDate: {
          $gt: '2020-01-01T00:00:00.000Z',
        },
        state: {$eq: 'ACTIVE'},
        hasIncident: false,
      },
    });

    expect(
      buildMutationRequestBody({
        searchParams: createSearchParams({
          endDateBefore: before,
          active: 'true',
        }),
        includeIds: [],
        excludeIds: [],
      }),
    ).toEqual({
      filter: {
        endDate: {
          $lt: '2020-01-02T00:00:00.000Z',
        },
        state: {$eq: 'ACTIVE'},
        hasIncident: false,
      },
    });
  });

  it('maps variable name and values to variables array from context', () => {
    const searchParams = createSearchParams({
      active: 'true',
    });

    const body: Body = buildMutationRequestBody({
      searchParams,
      includeIds: [],
      excludeIds: [],
      variableFilter: {
        name: 'foo',
        values: '"a","b"',
      },
    });

    expect(body).toEqual({
      filter: {
        variables: [{name: 'foo', value: {$in: ['"a"', '"b"']}}],
        state: {$eq: 'ACTIVE'},
        hasIncident: false,
      },
    });
  });

  it('maps completed and canceled flags to state correctly', () => {
    const both: Body = buildMutationRequestBody({
      searchParams: createSearchParams({
        completed: 'true',
        canceled: 'true',
      }),
      includeIds: [],
      excludeIds: [],
    });

    expect(both).toEqual({
      filter: {
        state: {$in: ['COMPLETED', 'TERMINATED']},
        hasIncident: false,
      },
    });

    const onlyCompleted: Body = buildMutationRequestBody({
      searchParams: createSearchParams({
        completed: 'true',
      }),
      includeIds: [],
      excludeIds: [],
    });

    expect(onlyCompleted).toEqual({
      filter: {
        state: {$eq: 'COMPLETED'},
        hasIncident: false,
      },
    });

    const onlyCanceled: Body = buildMutationRequestBody({
      searchParams: createSearchParams({
        canceled: 'true',
      }),
      includeIds: [],
      excludeIds: [],
    });

    expect(onlyCanceled).toEqual({
      filter: {
        state: {$eq: 'TERMINATED'},
        hasIncident: false,
      },
    });
  });

  it('returns empty filter when no search params provided', () => {
    const searchParams = createSearchParams({});

    const body: Body = buildMutationRequestBody({
      searchParams,
      includeIds: [],
      excludeIds: [],
    });

    expect(body).toEqual({
      filter: {},
    });
  });

  it('adds single variable', () => {
    const searchParams = createSearchParams({
      active: 'true',
    });

    const body: Body = buildMutationRequestBody({
      searchParams,
      includeIds: [],
      excludeIds: [],
      variableFilter: {
        name: 'status',
        values: '"pending"',
      },
    });

    expect(body).toEqual({
      filter: {
        state: {$eq: 'ACTIVE'},
        hasIncident: false,
        variables: [
          {
            name: 'status',
            value: '"pending"',
          },
        ],
      },
    });
  });

  it('adds multiple variable values with $in operator', () => {
    const searchParams = createSearchParams({
      active: 'true',
    });

    const body: Body = buildMutationRequestBody({
      searchParams,
      includeIds: [],
      excludeIds: [],
      variableFilter: {
        name: 'status',
        values: '"pending","active","completed"',
      },
    });

    expect(body).toEqual({
      filter: {
        state: {$eq: 'ACTIVE'},
        hasIncident: false,
        variables: [
          {
            name: 'status',
            value: {$in: ['"pending"', '"active"', '"completed"']},
          },
        ],
      },
    });
  });

  it('adds processDefinitionKey when present', () => {
    const searchParams = createSearchParams({
      active: 'true',
      flowNodeId: 'taskA',
    });

    const body: Body = buildMutationRequestBody({
      searchParams,
      processDefinitionKey: '2837942984928642',
    });

    expect(body).toEqual({
      filter: {
        hasIncident: false,
        state: {$eq: 'ACTIVE'},
        elementId: {$eq: 'taskA'},
        elementInstanceState: {$eq: 'ACTIVE'},
        processDefinitionKey: '2837942984928642',
      },
    });
  });
});
