/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {TasksSearchBody} from 'v1/api/types';
import {getQueryVariables} from './getQueryVariables';
import {storeStateLocally} from 'common/local-storage';

function mergeWithDefaultQuery(
  query: Partial<TasksSearchBody>,
): TasksSearchBody {
  return {
    pageSize: undefined,
    searchAfter: undefined,
    searchAfterOrEqual: undefined,
    searchBefore: undefined,
    searchBeforeOrEqual: undefined,
    ...query,
  };
}

describe('getQueryVariables()', () => {
  it('should return correct query body for all open tasks filter', () => {
    expect(
      getQueryVariables(
        {
          filter: 'all-open',
          sortBy: 'creation',
          sortOrder: 'asc',
        },
        {},
      ),
    ).toStrictEqual(
      mergeWithDefaultQuery({
        sort: [
          {
            field: 'creationTime',
            order: 'ASC',
          },
        ],
        state: 'CREATED',
      }),
    );
  });

  it('should return correct query body for assigned to me filter', () => {
    expect(
      getQueryVariables(
        {
          filter: 'assigned-to-me',
          sortBy: 'creation',
          sortOrder: 'asc',
        },
        {
          assignee: 'demo',
        },
      ),
    ).toStrictEqual(
      mergeWithDefaultQuery({
        sort: [
          {
            field: 'creationTime',
            order: 'ASC',
          },
        ],
        assigned: true,
        assignee: 'demo',
        state: 'CREATED',
      }),
    );
  });

  it('should return correct query body for unassigned filter', () => {
    expect(
      getQueryVariables(
        {
          filter: 'unassigned',
          sortBy: 'creation',
          sortOrder: 'asc',
        },
        {},
      ),
    ).toStrictEqual(
      mergeWithDefaultQuery({
        sort: [
          {
            field: 'creationTime',
            order: 'ASC',
          },
        ],
        assigned: false,
        state: 'CREATED',
      }),
    );
  });

  it('should return correct query body for completed filter', () => {
    expect(
      getQueryVariables(
        {
          filter: 'completed',
          sortBy: 'creation',
          sortOrder: 'asc',
        },
        {},
      ),
    ).toStrictEqual(
      mergeWithDefaultQuery({
        sort: [
          {
            field: 'creationTime',
            order: 'ASC',
          },
        ],
        state: 'COMPLETED',
      }),
    );
  });

  it('should return correct query body for custom filter', () => {
    storeStateLocally('customFilters', {
      custom: {
        assignee: 'all',
        status: 'all',
        variables: [
          {
            name: 'var1',
            value: 'value1',
          },
          {
            name: 'var2',
            value: 'value2',
          },
        ],
      },
    });
    expect(
      getQueryVariables(
        {
          filter: 'custom',
          sortBy: 'creation',
          sortOrder: 'asc',
        },
        {},
      ),
    ).toStrictEqual(
      mergeWithDefaultQuery({
        sort: [
          {
            field: 'creationTime',
            order: 'ASC',
          },
        ],
        taskVariables: [
          {
            name: 'var1',
            value: 'value1',
            operator: 'eq',
          },
          {
            name: 'var2',
            value: 'value2',
            operator: 'eq',
          },
        ],
      }),
    );
  });

  it('should return correct query body for persistable custom filter', () => {
    storeStateLocally('customFilters', {
      '55283997-c3c1-43c4-9b6d-9a1a3de0b2ac': {
        assignee: 'all',
        status: 'all',
        variables: [
          {
            name: 'var1',
            value: 'value1',
          },
          {
            name: 'var2',
            value: 'value2',
          },
        ],
      },
    });
    expect(
      getQueryVariables(
        {
          filter: '55283997-c3c1-43c4-9b6d-9a1a3de0b2ac',
          sortBy: 'creation',
          sortOrder: 'asc',
        },
        {
          assignee: 'demo',
        },
      ),
    ).toStrictEqual(
      mergeWithDefaultQuery({
        sort: [
          {
            field: 'creationTime',
            order: 'ASC',
          },
        ],
        taskVariables: [
          {
            name: 'var1',
            value: 'value1',
            operator: 'eq',
          },
          {
            name: 'var2',
            value: 'value2',
            operator: 'eq',
          },
        ],
      }),
    );
  });

  it('should return correct query body for page params', () => {
    expect(
      getQueryVariables(
        {
          filter: 'custom',
          sortBy: 'creation',
          sortOrder: 'asc',
        },
        {
          searchAfter: [
            '2023-08-25T15:41:45.322+0300',
            '2023-08-25T15:41:45.322+0300',
          ],
          searchAfterOrEqual: [
            '2023-08-25T15:41:45.322+0300',
            '2023-08-25T15:41:45.322+0300',
          ],
          searchBefore: [
            '2023-08-25T15:41:45.322+0300',
            '2023-08-25T15:41:45.322+0300',
          ],
          searchBeforeOrEqual: [
            '2023-08-25T15:41:45.322+0300',
            '2023-08-25T15:41:45.322+0300',
          ],
          pageSize: 10,
        },
      ),
    ).toStrictEqual({
      sort: [
        {
          field: 'creationTime',
          order: 'ASC',
        },
      ],
      pageSize: 10,
      searchAfter: [
        '2023-08-25T15:41:45.322+0300',
        '2023-08-25T15:41:45.322+0300',
      ],
      searchAfterOrEqual: [
        '2023-08-25T15:41:45.322+0300',
        '2023-08-25T15:41:45.322+0300',
      ],
      searchBefore: [
        '2023-08-25T15:41:45.322+0300',
        '2023-08-25T15:41:45.322+0300',
      ],
      searchBeforeOrEqual: [
        '2023-08-25T15:41:45.322+0300',
        '2023-08-25T15:41:45.322+0300',
      ],
      taskVariables: undefined,
    });
  });

  it('should return correct query body for sorting', () => {
    expect(
      getQueryVariables(
        {
          filter: 'all-open',
          sortBy: 'due',
          sortOrder: 'asc',
        },
        {},
      ),
    ).toStrictEqual(
      mergeWithDefaultQuery({
        sort: [
          {
            field: 'dueDate',
            order: 'ASC',
          },
        ],
        state: 'CREATED',
      }),
    );

    expect(
      getQueryVariables(
        {
          filter: 'all-open',
          sortBy: 'due',
          sortOrder: 'desc',
        },
        {},
      ),
    ).toStrictEqual(
      mergeWithDefaultQuery({
        sort: [
          {
            field: 'dueDate',
            order: 'DESC',
          },
        ],
        state: 'CREATED',
      }),
    );

    expect(
      getQueryVariables(
        {
          filter: 'all-open',
          sortBy: 'follow-up',
          sortOrder: 'asc',
        },
        {},
      ),
    ).toStrictEqual(
      mergeWithDefaultQuery({
        sort: [
          {
            field: 'followUpDate',
            order: 'ASC',
          },
        ],
        state: 'CREATED',
      }),
    );

    expect(
      getQueryVariables(
        {
          filter: 'all-open',
          sortBy: 'follow-up',
          sortOrder: 'desc',
        },
        {},
      ),
    ).toStrictEqual(
      mergeWithDefaultQuery({
        sort: [
          {
            field: 'followUpDate',
            order: 'DESC',
          },
        ],
        state: 'CREATED',
      }),
    );

    expect(
      getQueryVariables(
        {
          filter: 'all-open',
          sortBy: 'completion',
          sortOrder: 'asc',
        },
        {},
      ),
    ).toStrictEqual(
      mergeWithDefaultQuery({
        sort: [
          {
            field: 'completionTime',
            order: 'ASC',
          },
        ],
        state: 'CREATED',
      }),
    );

    expect(
      getQueryVariables(
        {
          filter: 'all-open',
          sortBy: 'completion',
          sortOrder: 'desc',
        },
        {},
      ),
    ).toStrictEqual(
      mergeWithDefaultQuery({
        sort: [
          {
            field: 'completionTime',
            order: 'DESC',
          },
        ],
        state: 'CREATED',
      }),
    );
  });
});
