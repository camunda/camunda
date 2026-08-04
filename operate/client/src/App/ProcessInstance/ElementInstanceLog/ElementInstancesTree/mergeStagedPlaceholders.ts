/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QuerySortOrder} from '@camunda/camunda-api-zod-schemas/8.10';

type MergeStagedPlaceholdersParams<TItem, TPlaceholder> = {
  items: TItem[];
  stagedPlaceholders: TPlaceholder[];
  sortOrder: QuerySortOrder;
  hasNextPage: boolean;
  hasPreviousPage: boolean;
};

/**
 * A staged placeholder stands for an element instance that has not started yet,
 * so it belongs at the newest end of the scope: before the loaded items when
 * sorted latest-first, after them when sorted oldest-first. That end is only
 * inside the loaded window when the scope is not paginated past it, and a
 * placeholder is dropped otherwise - rendering it against an unrelated
 * neighbour would misstate where the element is about to run.
 */
function mergeStagedPlaceholders<TItem, TPlaceholder>({
  items,
  stagedPlaceholders,
  sortOrder,
  hasNextPage,
  hasPreviousPage,
}: MergeStagedPlaceholdersParams<TItem, TPlaceholder>): (
  TItem | TPlaceholder
)[] {
  if (stagedPlaceholders.length === 0) {
    return items;
  }

  if (sortOrder === 'desc') {
    return hasPreviousPage ? items : [...stagedPlaceholders, ...items];
  }

  return hasNextPage ? items : [...items, ...stagedPlaceholders];
}

export {mergeStagedPlaceholders};
