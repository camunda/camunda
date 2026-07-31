/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QuerySortOrder} from '@camunda/camunda-api-zod-schemas/8.10';
import {mergeStagedPlaceholders} from './mergeStagedPlaceholders';

const items = ['oldest', 'newest'];
const stagedPlaceholders = ['staged'];

type Scenario = {
  name: string;
  sortOrder: QuerySortOrder;
  hasNextPage: boolean;
  hasPreviousPage: boolean;
  expected: string[];
};

describe('mergeStagedPlaceholders', () => {
  const scenarios: Scenario[] = [
    {
      name: 'puts them before the items when the newest end is loaded, latest-first',
      sortOrder: 'desc',
      hasNextPage: false,
      hasPreviousPage: false,
      expected: ['staged', 'oldest', 'newest'],
    },
    {
      name: 'puts them after the items when the newest end is loaded, oldest-first',
      sortOrder: 'asc',
      hasNextPage: false,
      hasPreviousPage: false,
      expected: ['oldest', 'newest', 'staged'],
    },
    {
      name: 'drops them when latest-first has paged past the newest end',
      sortOrder: 'desc',
      hasNextPage: false,
      hasPreviousPage: true,
      expected: items,
    },
    {
      name: 'drops them when oldest-first has paged past the newest end',
      sortOrder: 'asc',
      hasNextPage: true,
      hasPreviousPage: false,
      expected: items,
    },
    {
      name: 'keeps them latest-first while older pages remain unloaded',
      sortOrder: 'desc',
      hasNextPage: true,
      hasPreviousPage: false,
      expected: ['staged', 'oldest', 'newest'],
    },
    {
      name: 'keeps them oldest-first while older pages remain unloaded',
      sortOrder: 'asc',
      hasNextPage: false,
      hasPreviousPage: true,
      expected: ['oldest', 'newest', 'staged'],
    },
  ];

  it.each(scenarios)(
    '$name',
    ({sortOrder, hasNextPage, hasPreviousPage, expected}) => {
      expect(
        mergeStagedPlaceholders({
          items,
          stagedPlaceholders,
          sortOrder,
          hasNextPage,
          hasPreviousPage,
        }),
      ).toEqual(expected);
    },
  );

  it.each<QuerySortOrder>(['asc', 'desc'])(
    'returns the items untouched when nothing is staged, %s',
    (sortOrder) => {
      expect(
        mergeStagedPlaceholders({
          items,
          stagedPlaceholders: [],
          sortOrder,
          hasNextPage: false,
          hasPreviousPage: false,
        }),
      ).toEqual(items);
    },
  );

  it('renders a staged placeholder into a scope that has no items yet', () => {
    expect(
      mergeStagedPlaceholders({
        items: [],
        stagedPlaceholders,
        sortOrder: 'desc',
        hasNextPage: false,
        hasPreviousPage: false,
      }),
    ).toEqual(stagedPlaceholders);
  });
});
