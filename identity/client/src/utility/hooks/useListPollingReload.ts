/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useCallback, useRef } from "react";
import { usePollingReload } from "./usePollingReload";

type ListResponse<T> = {
  items: T[];
  page?: {
    totalItems: number;
  };
};

type RefetchResult<T> = {
  data?: ListResponse<T>;
  isSuccess: boolean;
};

const useListPollingReload = <T extends object>(
  reload: () => Promise<RefetchResult<T>>,
  items: T[],
  itemKey: keyof T,
  totalItems?: number,
) => {
  const expectedTotalItems = useRef<
    { count: number; direction: 1 | -1 } | undefined
  >(undefined);

  const hasListChanged = useCallback(
    (current: ListResponse<T>) => {
      const expected = expectedTotalItems.current;
      if (expected && current.page) {
        const hasReachedExpectedCount =
          expected.direction === 1
            ? current.page.totalItems >= expected.count
            : current.page.totalItems <= expected.count;

        if (hasReachedExpectedCount) {
          return true;
        }
      }

      if (items.length !== current.items.length) {
        return true;
      }

      return items.some(
        (previousItem) =>
          !current.items.some(
            (currentItem) => currentItem[itemKey] === previousItem[itemKey],
          ),
      );
    },
    [itemKey, items],
  );

  const { startPolling: startBasePolling, ...pollingState } = usePollingReload(
    reload,
    hasListChanged,
  );
  const startPolling = useCallback(
    (itemCountDelta: number) => {
      expectedTotalItems.current =
        totalItems === undefined
          ? undefined
          : {
              count: totalItems + itemCountDelta,
              direction: itemCountDelta > 0 ? 1 : -1,
            };
      startBasePolling();
    },
    [startBasePolling, totalItems],
  );

  return { ...pollingState, startPolling };
};

export { useListPollingReload };
