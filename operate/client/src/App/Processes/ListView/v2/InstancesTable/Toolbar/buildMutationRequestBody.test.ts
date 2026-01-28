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
        elementId: {$eq: 'taskA'},
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
        elementId: {$eq: 'taskA'},
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
        elementId: {$eq: 'taskA'},
        hasIncident: true,
        processInstanceKey: {$in: ['1', '2'], $notIn: ['3']},
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
        elementId: {$eq: 'taskA'},
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
        elementId: {$eq: 'taskA'},
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
        elementId: {$eq: 'taskA'},
        $or: [{state: {$in: ['ACTIVE']}}, {hasIncident: true}],
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
        elementId: {$eq: 'taskA'},
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
        elementId: {$eq: 'taskA'},
        state: {$eq: 'ACTIVE'},
      },
    });
  });

  it('maps additional baseFilter fields to request body', () => {
    const body: Body = buildMutationRequestBody({
      baseFilter: {
        activityId: 'taskA',
        errorMessage: 'some error',
        tenantId: 'tenant-xyz',
        batchOperationId: 'batch-123',
        parentInstanceId: 'parent-456',
        retriesLeft: true,
        incidentErrorHashCode: 37136123613781,
      },
      includeIds: [],
      excludeIds: [],
    });

    expect(body).toEqual({
      filter: {
        elementId: {$eq: 'taskA'},
        errorMessage: {$in: ['some error']},
        tenantId: {$eq: 'tenant-xyz'},
        batchOperationId: {$eq: 'batch-123'},
        parentProcessInstanceKey: {$eq: 'parent-456'},
        hasRetriesLeft: true,
        incidentErrorHashCode: 37136123613781,
      },
    });
  });

  it('maps processIds to processDefinitionKey $in', () => {
    const body: Body = buildMutationRequestBody({
      baseFilter: {
        activityId: 'taskA',
        incidents: false,
        processIds: ['p1', 'p2'],
      },
      includeIds: [],
      excludeIds: [],
    });

    expect(body).toEqual({
      filter: {
        elementId: {$eq: 'taskA'},
        processDefinitionKey: {$in: ['p1', 'p2']},
      },
    });
  });

  it('maps startDateAfter / startDateBefore to startDate $gt/$lt', () => {
    const after = '2020-01-01T00:00:00Z';
    const before = '2020-01-02T00:00:00Z';

    expect(
      buildMutationRequestBody({
        baseFilter: {
          startDateAfter: after,
          startDateBefore: before,
        },
        includeIds: [],
        excludeIds: [],
      }),
    ).toEqual({
      filter: {
        startDate: {
          $gt: '2020-01-01T00:00:00.000Z',
          $lt: '2020-01-02T00:00:00.000Z',
        },
      },
    });

    expect(
      buildMutationRequestBody({
        baseFilter: {
          startDateAfter: after,
        },
        includeIds: [],
        excludeIds: [],
      }),
    ).toEqual({
      filter: {
        startDate: {
          $gt: '2020-01-01T00:00:00.000Z',
        },
      },
    });

    expect(
      buildMutationRequestBody({
        baseFilter: {
          startDateBefore: before,
        },
        includeIds: [],
        excludeIds: [],
      }),
    ).toEqual({
      filter: {
        startDate: {
          $lt: '2020-01-02T00:00:00.000Z',
        },
      },
    });
  });

  it('maps endDateAfter / endDateBefore to endDate $gt/$lt', () => {
    const after = '2020-01-01T00:00:00Z';
    const before = '2020-01-02T00:00:00Z';

    expect(
      buildMutationRequestBody({
        baseFilter: {
          endDateAfter: after,
          endDateBefore: before,
        },
        includeIds: [],
        excludeIds: [],
      }),
    ).toEqual({
      filter: {
        endDate: {
          $gt: '2020-01-01T00:00:00.000Z',
          $lt: '2020-01-02T00:00:00.000Z',
        },
      },
    });

    expect(
      buildMutationRequestBody({
        baseFilter: {
          endDateAfter: after,
        },
        includeIds: [],
        excludeIds: [],
      }),
    ).toEqual({
      filter: {
        endDate: {
          $gt: '2020-01-01T00:00:00.000Z',
        },
      },
    });

    expect(
      buildMutationRequestBody({
        baseFilter: {
          endDateBefore: before,
        },
        includeIds: [],
        excludeIds: [],
      }),
    ).toEqual({
      filter: {
        endDate: {
          $lt: '2020-01-02T00:00:00.000Z',
        },
      },
    });
  });

  it('maps variable name and values to variables array', () => {
    const body: Body = buildMutationRequestBody({
      baseFilter: {
        incidents: false,
        variable: {name: 'foo', values: ['a', 'b']},
      },
      includeIds: [],
      excludeIds: [],
    });

    expect(body).toEqual({
      filter: {
        variables: [
          {name: 'foo', value: 'a'},
          {name: 'foo', value: 'b'},
        ],
      },
    });
  });

  it('maps completed and canceled flags to state correctly', () => {
    const both: Body = buildMutationRequestBody({
      baseFilter: {
        completed: true,
        canceled: true,
      },
      includeIds: [],
      excludeIds: [],
    });

    expect(both).toEqual({
      filter: {
        state: {$in: ['COMPLETED', 'TERMINATED']},
      },
    });

    const onlyCompleted: Body = buildMutationRequestBody({
      baseFilter: {
        completed: true,
      },
      includeIds: [],
      excludeIds: [],
    });

    expect(onlyCompleted).toEqual({
      filter: {
        state: {$eq: 'COMPLETED'},
      },
    });

    const onlyCanceled: Body = buildMutationRequestBody({
      baseFilter: {
        canceled: true,
      },
      includeIds: [],
      excludeIds: [],
    });

    expect(onlyCanceled).toEqual({
      filter: {
        state: {$eq: 'TERMINATED'},
      },
    });
  });
});
