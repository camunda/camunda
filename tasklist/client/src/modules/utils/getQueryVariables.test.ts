/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getQueryVariables} from './getQueryVariables';
import {storeStateLocally} from './localStorage';

describe('getQueryVariables()', () => {
  it('should return correct query body for all open tasks filter', () => {
    expect(
      getQueryVariables(
        {
          filter: 'all-open',
          sortBy: 'creation',
          sortOrder: 'asc',
        },
        {
          pageSize: 10,
        },
      ),
    ).toStrictEqual({
      sort: [
        {
          field: 'creationDate',
          order: 'asc',
        },
      ],
      filter: {
        state: 'CREATED',
      },
      page: {
        limit: 10,
      },
    });
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
          currentUserId: 'demo',
          pageSize: 10,
        },
      ),
    ).toStrictEqual({
      sort: [
        {
          field: 'creationDate',
          order: 'asc',
        },
      ],
      filter: {
        assignee: 'demo',
        state: 'CREATED',
      },
      page: {
        limit: 10,
      },
    });
  });

  it('should return correct query body for unassigned filter', () => {
    expect(
      getQueryVariables(
        {
          filter: 'unassigned',
          sortBy: 'creation',
          sortOrder: 'asc',
        },
        {
          pageSize: 10,
        },
      ),
    ).toStrictEqual({
      sort: [
        {
          field: 'creationDate',
          order: 'asc',
        },
      ],
      filter: {
        state: 'CREATED',
      },
      page: {
        limit: 10,
      },
    });
  });

  it('should return correct query body for completed filter', () => {
    expect(
      getQueryVariables(
        {
          filter: 'completed',
          sortBy: 'creation',
          sortOrder: 'asc',
        },
        {
          pageSize: 10,
        },
      ),
    ).toStrictEqual({
      sort: [
        {
          field: 'creationDate',
          order: 'asc',
        },
      ],
      filter: {
        state: 'COMPLETED',
      },
      page: {
        limit: 10,
      },
    });
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
        {
          pageSize: 10,
        },
      ),
    ).toStrictEqual({
      sort: [
        {
          field: 'creationDate',
          order: 'asc',
        },
      ],
      filter: {
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
      page: {
        limit: 10,
      },
    });
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
          currentUserId: 'demo',
          pageSize: 10,
        },
      ),
    ).toStrictEqual({
      sort: [
        {
          field: 'creationDate',
          order: 'asc',
        },
      ],
      filter: {
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
      page: {
        limit: 10,
      },
    });
  });

  it('should return correct query body for page params', () => {
    expect(
      getQueryVariables(
        {
          filter: 'custom',
          sortBy: 'creation',
          sortOrder: 'asc',
          candidateGroup: 'admin',
        },
        {
          pageSize: 10,
        },
      ),
    ).toStrictEqual({
      sort: [
        {
          field: 'creationDate',
          order: 'asc',
        },
      ],
      filter: {
        candidateGroups: ['admin'],
      },
      page: {
        limit: 10,
      },
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
        {
          pageSize: 10,
        },
      ),
    ).toStrictEqual({
      sort: [
        {
          field: 'dueDate',
          order: 'asc',
        },
      ],
      filter: {
        state: 'CREATED',
      },
      page: {
        limit: 10,
      },
    });

    expect(
      getQueryVariables(
        {
          filter: 'all-open',
          sortBy: 'due',
          sortOrder: 'desc',
        },
        {
          pageSize: 10,
        },
      ),
    ).toStrictEqual({
      sort: [
        {
          field: 'dueDate',
          order: 'desc',
        },
      ],
      filter: {
        state: 'CREATED',
      },
      page: {
        limit: 10,
      },
    });

    expect(
      getQueryVariables(
        {
          filter: 'all-open',
          sortBy: 'follow-up',
          sortOrder: 'asc',
        },
        {
          pageSize: 10,
        },
      ),
    ).toStrictEqual({
      sort: [
        {
          field: 'followUpDate',
          order: 'asc',
        },
      ],
      filter: {
        state: 'CREATED',
      },
      page: {
        limit: 10,
      },
    });

    expect(
      getQueryVariables(
        {
          filter: 'all-open',
          sortBy: 'follow-up',
          sortOrder: 'desc',
        },
        {
          pageSize: 10,
        },
      ),
    ).toStrictEqual({
      sort: [
        {
          field: 'followUpDate',
          order: 'desc',
        },
      ],
      filter: {
        state: 'CREATED',
      },
      page: {
        limit: 10,
      },
    });

    expect(
      getQueryVariables(
        {
          filter: 'all-open',
          sortBy: 'completion',
          sortOrder: 'asc',
        },
        {
          pageSize: 10,
        },
      ),
    ).toStrictEqual({
      sort: [
        {
          field: 'completionDate',
          order: 'asc',
        },
      ],
      filter: {
        state: 'CREATED',
      },
      page: {
        limit: 10,
      },
    });

    expect(
      getQueryVariables(
        {
          filter: 'all-open',
          sortBy: 'completion',
          sortOrder: 'desc',
        },
        {
          pageSize: 10,
        },
      ),
    ).toStrictEqual({
      sort: [
        {
          field: 'completionDate',
          order: 'desc',
        },
      ],
      filter: {
        state: 'CREATED',
      },
      page: {
        limit: 10,
      },
    });
  });
});
