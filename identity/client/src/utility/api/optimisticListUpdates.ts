/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type { QueryClient, QueryKey } from "@tanstack/react-query";

type SearchData<T> = { items: T[]; page?: { totalItems: number } };

type SearchParams = {
  filter?: Record<string, unknown>;
  sort?: unknown[];
  page?: { from?: number; limit?: number };
} & Record<string, unknown>;

const POLL_INTERVAL_MS = 1000;
const POLL_ATTEMPTS = 10;

// Contract: params must be the element right after the search key, e.g.
// ["roles", "search", params] or ["tenants", "roles", tenantId, params]. A key
// of another shape makes scope matching fail, so the patch skips that cache.
const paramsOf = (queryKey: QueryKey, searchKey: QueryKey) =>
  queryKey[searchKey.length] as SearchParams | undefined;

type SortConfig = { field: string; order?: string };

type Scope = Record<string, unknown>;

const matchesScope = (queryKey: QueryKey, searchKey: QueryKey, scope?: Scope) =>
  Object.entries(scope ?? {}).every(
    ([key, value]) => paramsOf(queryKey, searchKey)?.[key] === value,
  );

const isUnfiltered = (params: SearchParams | undefined, scope: Scope = {}) => {
  if (!params) return true;
  const { filter, ...rest } = params;
  const hasFilter = filter !== undefined && Object.keys(filter).length > 0;
  const hasExtraFilter = Object.entries(rest).some(
    ([key, v]) =>
      key !== "sort" && key !== "page" && !(key in scope) && v !== undefined,
  );
  return !hasFilter && !hasExtraFilter;
};

const compareBySort = <T>(a: T, b: T, sort: SortConfig[]) => {
  for (const { field, order } of sort) {
    const valueA = String((a as Record<string, unknown>)[field] ?? "");
    const valueB = String((b as Record<string, unknown>)[field] ?? "");
    const result = valueA.localeCompare(valueB);
    if (result !== 0) {
      return result * (order?.toUpperCase() === "DESC" ? -1 : 1);
    }
  }
  return 0;
};

// A fetch started before the write would overwrite the patch with stale data.
const cancelListQueries = (
  qc: QueryClient,
  searchKey: QueryKey,
  scope?: Scope,
) =>
  qc.cancelQueries({
    queryKey: searchKey,
    predicate: (query) => matchesScope(query.queryKey, searchKey, scope),
  });

// Without a sort the backend is assumed to list in creation order.
const placeInFirstPage = <T>(
  current: T[],
  item: T,
  params: SearchParams | undefined,
) => {
  if (params?.page?.from) return undefined;
  const sort = (params?.sort ?? []) as SortConfig[];
  const items = [...current, item];
  if (sort.length > 0) items.sort((a, b) => compareBySort(a, b, sort));
  const limit = params?.page?.limit;
  if (limit !== undefined && items.indexOf(item) >= limit) return undefined;
  return limit === undefined ? items : items.slice(0, limit);
};

// The total is bumped even when the row is not shown, so pagination stays right.
// A row that is already cached came from a refetch that has counted it.
const patchInsert = <T>(
  qc: QueryClient,
  searchKey: QueryKey,
  item: T | undefined,
  matches: (item: T) => boolean,
  scope?: Scope,
) => {
  for (const [queryKey, data] of qc.getQueriesData<SearchData<T>>({
    queryKey: searchKey,
  })) {
    const params = paramsOf(queryKey, searchKey);
    if (
      !data ||
      !matchesScope(queryKey, searchKey, scope) ||
      !isUnfiltered(params, scope) ||
      data.items.some(matches)
    ) {
      continue;
    }
    const items =
      item === undefined
        ? undefined
        : placeInFirstPage(data.items, item, params);
    qc.setQueryData<SearchData<T>>(queryKey, {
      ...data,
      items: items ?? data.items,
      page: data.page && { ...data.page, totalItems: data.page.totalItems + 1 },
    });
  }
};

const patchUpdate = <T>(
  qc: QueryClient,
  searchKey: QueryKey,
  matches: (item: T) => boolean,
  patch: Partial<T>,
) => {
  for (const [queryKey, data] of qc.getQueriesData<SearchData<T>>({
    queryKey: searchKey,
  })) {
    if (!data?.items.some(matches)) continue;
    qc.setQueryData<SearchData<T>>(queryKey, {
      ...data,
      items: data.items.map((item) =>
        matches(item) ? { ...item, ...patch } : item,
      ),
    });
  }
};

const patchRemove = <T>(
  qc: QueryClient,
  searchKey: QueryKey,
  matches: (item: T) => boolean,
  scope?: Scope,
) => {
  for (const [queryKey, data] of qc.getQueriesData<SearchData<T>>({
    queryKey: searchKey,
  })) {
    if (!data || !matchesScope(queryKey, searchKey, scope)) continue;
    const present = data.items.some(matches);
    if (!present && !isUnfiltered(paramsOf(queryKey, searchKey), scope)) {
      continue;
    }
    qc.setQueryData<SearchData<T>>(queryKey, {
      ...data,
      items: present ? data.items.filter((item) => !matches(item)) : data.items,
      page: data.page && {
        ...data.page,
        totalItems: Math.max(0, data.page.totalItems - 1),
      },
    });
  }
};

export const insertIntoListCaches = async <T>(
  qc: QueryClient,
  searchKey: QueryKey,
  item: T,
  matches: (item: T) => boolean,
  scope?: Scope,
) => {
  await cancelListQueries(qc, searchKey, scope);
  patchInsert(qc, searchKey, item, matches, scope);
};

export const updateInListCaches = async <T>(
  qc: QueryClient,
  searchKey: QueryKey,
  matches: (item: T) => boolean,
  patch: Partial<T>,
) => {
  await cancelListQueries(qc, searchKey);
  patchUpdate(qc, searchKey, matches, patch);
};

export const removeFromListCaches = async <T>(
  qc: QueryClient,
  searchKey: QueryKey,
  matches: (item: T) => boolean,
  scope?: Scope,
) => {
  await cancelListQueries(qc, searchKey, scope);
  patchRemove(qc, searchKey, matches, scope);
};

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

const pollUntil = async (isReady: () => Promise<boolean>) => {
  for (let attempt = 0; attempt < POLL_ATTEMPTS; attempt++) {
    try {
      if (await isReady()) return;
    } catch {
      // retried like an unconfirmed poll
    }
    await sleep(POLL_INTERVAL_MS);
  }
};

// Invalidates even when never confirmed, so the list falls back to the backend.
export const confirmThenInvalidate = async <T>(
  qc: QueryClient,
  invalidateKey: QueryKey,
  search: () => Promise<SearchData<T>>,
  isReady: (items: T[]) => boolean,
) => {
  try {
    await pollUntil(async () => isReady((await search()).items));
  } finally {
    await qc.invalidateQueries({ queryKey: invalidateKey });
  }
};

const cachedTotalItems = (qc: QueryClient, searchKey: QueryKey, scope: Scope) =>
  qc
    .getQueryCache()
    .findAll({ queryKey: searchKey, type: "active" })
    .filter(
      (query) =>
        query.state.data !== undefined &&
        matchesScope(query.queryKey, searchKey, scope) &&
        isUnfiltered(paramsOf(query.queryKey, searchKey), scope),
    )
    .sort((a, b) => b.state.dataUpdatedAt - a.state.dataUpdatedAt)
    .map((query) => (query.state.data as SearchData<unknown>).page?.totalItems)
    .find((total) => total !== undefined);

type MembershipChange<T> =
  | { type: "assign"; item?: T; matches: (item: T) => boolean }
  | { type: "unassign"; matches: (item: T) => boolean };

// Changes of one table that are still waiting for the backend. Later changes
// in a batch join the running poll instead of measuring a baseline of their own
// from a cache that a refetch may already have replaced.
const pendingChanges = new Map<string, { baseline: number; delta: number }>();

// These searches cannot filter by ID, so a change counts as confirmed once
// totalItems has moved by the pending delta from the total cached before.
export const applyMembershipChange = async <T>(
  qc: QueryClient,
  {
    searchKey,
    scope,
    change,
    search,
    invalidateKeys,
  }: {
    searchKey: QueryKey;
    scope: Scope;
    change: MembershipChange<T>;
    search: () => Promise<SearchData<T>>;
    invalidateKeys: QueryKey[];
  },
) => {
  await cancelListQueries(qc, searchKey, scope);
  const step = change.type === "assign" ? 1 : -1;
  const key = JSON.stringify([searchKey, scope]);
  const pending = pendingChanges.get(key);
  const baseline = pending ? undefined : cachedTotalItems(qc, searchKey, scope);
  if (change.type === "assign") {
    patchInsert(qc, searchKey, change.item, change.matches, scope);
  } else {
    patchRemove(qc, searchKey, change.matches, scope);
  }

  if (pending) {
    pending.delta += step;
    return;
  }

  const invalidate = () =>
    Promise.all(
      invalidateKeys.map((queryKey) => qc.invalidateQueries({ queryKey })),
    );
  if (baseline === undefined) {
    await invalidate();
    return;
  }

  const state = { baseline, delta: step };
  pendingChanges.set(key, state);
  try {
    await pollUntil(async () => {
      const total = (await search()).page?.totalItems;
      const expected = state.baseline + state.delta;
      // Opposite changes net to zero, which proves nothing about either write.
      return (
        total !== undefined &&
        state.delta !== 0 &&
        (state.delta > 0 ? total >= expected : total <= expected)
      );
    });
  } finally {
    pendingChanges.delete(key);
    await invalidate();
  }
};

type SubTableConfig<T> = {
  searchKey: QueryKey;
  scope?: Scope;
  search: () => Promise<SearchData<T>>;
  invalidateKeys?: QueryKey[];
};

export const syncSubTable = <T>(
  qc: QueryClient,
  { searchKey, scope = {}, search, invalidateKeys }: SubTableConfig<T>,
  change: MembershipChange<T>,
) => {
  void applyMembershipChange(qc, {
    searchKey,
    scope,
    change,
    search,
    invalidateKeys: invalidateKeys ?? [searchKey],
  });
};
