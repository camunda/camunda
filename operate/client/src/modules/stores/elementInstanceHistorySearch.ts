/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.10';
import {makeObservable, observable, action, override} from 'mobx';
import {searchElementInstances} from 'modules/api/v2/elementInstances/searchElementInstances';
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from 'modules/stores/networkReconnectionHandler';
import type {RequestError} from 'modules/request';
import {HTTP_STATUS_FORBIDDEN} from 'modules/constants/statusCode';
import {escapeLikePattern} from 'modules/utils/escapeLikePattern';

const PAGE_SIZE = 50;
const POLLING_INTERVAL = 5000;
const DEBOUNCE_MS = 300;

type PageMetadata = {
  totalItems: number;
  windowStart: number;
  windowEnd: number;
};

type Status = 'idle' | 'loading' | 'error' | 'error-permissions' | 'loaded';

type State = {
  searchText: string;
  processInstanceKey: string | null;
  items: ElementInstance[];
  pageMetadata: PageMetadata;
  status: Status;
};

const DEFAULT_STATE: State = {
  searchText: '',
  processInstanceKey: null,
  items: [],
  pageMetadata: {totalItems: 0, windowStart: 0, windowEnd: 0},
  status: 'idle',
};

class ElementInstanceHistorySearch extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};

  private debounceTimerId: ReturnType<typeof setTimeout> | null = null;
  private searchAbortController: AbortController | null = null;
  private pollAbortController: AbortController | null = null;
  private pollIntervalId: ReturnType<typeof setInterval> | null = null;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      setSearchText: action,
      setProcessInstanceKey: action,
      setItems: action,
      setStatus: action,
      reset: override,
    });
  }

  /**
   * Updates the search text and schedules a debounced fetch.
   * The debounce coalesces rapid typing into a single API call after
   * {@link DEBOUNCE_MS} of inactivity.
   */
  setSearchText = (searchText: string) => {
    if (this.state.searchText === searchText) {
      return;
    }
    this.state.searchText = searchText;

    if (this.debounceTimerId !== null) {
      clearTimeout(this.debounceTimerId);
      this.debounceTimerId = null;
    }

    if (this.hasActiveSearch && this.state.processInstanceKey !== null) {
      this.debounceTimerId = setTimeout(() => {
        this.debounceTimerId = null;
        this.fetchFirstPage();
      }, DEBOUNCE_MS);
    } else {
      this.stopPolling();
      this.setItems([]);
      this.setStatus('idle');
    }
  };

  setProcessInstanceKey = (processInstanceKey: string | null) => {
    if (this.state.processInstanceKey === processInstanceKey) {
      return;
    }
    // Switching instance: clear any in-flight work and reset to default state
    // while keeping the new process instance key.
    this.reset();
    this.state.processInstanceKey = processInstanceKey;
  };

  setItems = (items: ElementInstance[]) => {
    this.state.items = items;
  };

  setStatus = (status: Status) => {
    this.state.status = status;
  };

  get hasActiveSearch(): boolean {
    return this.state.searchText.trim().length > 0;
  }

  hasNextPage = (): boolean => {
    return (
      this.state.pageMetadata.windowEnd < this.state.pageMetadata.totalItems
    );
  };

  hasPreviousPage = (): boolean => {
    return this.state.pageMetadata.windowStart > 0;
  };

  fetchFirstPage = async () => {
    const processInstanceKey = this.state.processInstanceKey;
    if (processInstanceKey === null || !this.hasActiveSearch) {
      return;
    }

    this.cancelInFlightSearch();
    this.setStatus('loading');

    this.searchAbortController = new AbortController();
    const controller = this.searchAbortController;
    const requestedSearchText = this.state.searchText;

    const {response, error} = await searchElementInstances(
      this.buildPayload(processInstanceKey, 0),
      controller.signal,
    );

    if (controller.signal.aborted) {
      return;
    }

    // Discard if the search text changed since dispatch.
    if (this.state.searchText !== requestedSearchText) {
      return;
    }

    if (response !== null) {
      this.state.items = response.items;
      this.state.pageMetadata = {
        totalItems: response.page.totalItems,
        windowStart: 0,
        windowEnd: PAGE_SIZE,
      };
      this.state.status = 'loaded';
      this.startPolling();
    } else {
      this.state.status = this.resolveErrorStatus(error);
      logger.error('Failed to fetch element instance history search', error);
    }
  };

  fetchNextPage = async (): Promise<number> => {
    const processInstanceKey = this.state.processInstanceKey;
    if (processInstanceKey === null || !this.hasActiveSearch) {
      return -1;
    }

    const nextWindowStart = this.state.pageMetadata.windowEnd;
    const nextWindowEnd = nextWindowStart + PAGE_SIZE;

    if (nextWindowStart >= this.state.pageMetadata.totalItems) {
      return 0;
    }

    this.cancelInFlightSearch();
    this.setStatus('loading');

    this.searchAbortController = new AbortController();
    const controller = this.searchAbortController;
    const requestedSearchText = this.state.searchText;

    const {response, error} = await searchElementInstances(
      this.buildPayload(processInstanceKey, nextWindowStart),
      controller.signal,
    );

    if (controller.signal.aborted) {
      return -1;
    }
    if (this.state.searchText !== requestedSearchText) {
      return -1;
    }

    if (response !== null) {
      this.state.items = response.items;
      this.state.pageMetadata = {
        totalItems: response.page.totalItems,
        windowStart: nextWindowStart,
        windowEnd: nextWindowEnd,
      };
      this.state.status = 'loaded';
      return Math.max(0, response.items.length - PAGE_SIZE);
    } else {
      this.state.status = this.resolveErrorStatus(error);
      logger.error('Failed to fetch next page of search results', error);
      return -1;
    }
  };

  fetchPreviousPage = async (): Promise<number> => {
    const processInstanceKey = this.state.processInstanceKey;
    if (processInstanceKey === null || !this.hasActiveSearch) {
      return -1;
    }

    const prevWindowEnd = this.state.pageMetadata.windowStart;
    const prevWindowStart = Math.max(0, prevWindowEnd - PAGE_SIZE);

    if (this.state.pageMetadata.windowStart === 0) {
      return 0;
    }

    this.cancelInFlightSearch();
    this.setStatus('loading');

    this.searchAbortController = new AbortController();
    const controller = this.searchAbortController;
    const requestedSearchText = this.state.searchText;

    const {response, error} = await searchElementInstances(
      this.buildPayload(processInstanceKey, prevWindowStart),
      controller.signal,
    );

    if (controller.signal.aborted) {
      return -1;
    }
    if (this.state.searchText !== requestedSearchText) {
      return -1;
    }

    if (response !== null) {
      this.state.items = response.items;
      this.state.pageMetadata = {
        totalItems: response.page.totalItems,
        windowStart: prevWindowStart,
        windowEnd: prevWindowEnd,
      };
      this.state.status = 'loaded';
      return Math.min(PAGE_SIZE, response.items.length);
    } else {
      this.state.status = this.resolveErrorStatus(error);
      logger.error('Failed to fetch previous page of search results', error);
      return -1;
    }
  };

  startPolling = () => {
    if (this.pollIntervalId !== null) {
      return;
    }
    this.pollIntervalId = setInterval(() => {
      this.pollOnce();
    }, POLLING_INTERVAL);
  };

  stopPolling = () => {
    if (this.pollIntervalId !== null) {
      clearInterval(this.pollIntervalId);
      this.pollIntervalId = null;
    }
    if (this.pollAbortController !== null) {
      this.pollAbortController.abort();
      this.pollAbortController = null;
    }
  };

  private pollOnce = async () => {
    if (document.visibilityState === 'hidden') {
      return;
    }
    const processInstanceKey = this.state.processInstanceKey;
    if (processInstanceKey === null || !this.hasActiveSearch) {
      return;
    }
    if (this.pollAbortController !== null) {
      // Previous poll still in flight; skip this tick.
      return;
    }

    this.pollAbortController = new AbortController();
    const signal = this.pollAbortController.signal;
    const requestedWindowStart = this.state.pageMetadata.windowStart;
    const requestedSearchText = this.state.searchText;

    try {
      const {response, error} = await searchElementInstances(
        this.buildPayload(processInstanceKey, requestedWindowStart),
        signal,
      );

      if (signal.aborted) {
        return;
      }

      // Discard if the user typed something else or scrolled to a different
      // page since this poll was dispatched.
      if (
        this.state.searchText !== requestedSearchText ||
        this.state.pageMetadata.windowStart !== requestedWindowStart
      ) {
        return;
      }

      if (response !== null) {
        this.state.items = response.items;
        this.state.pageMetadata = {
          ...this.state.pageMetadata,
          totalItems: response.page.totalItems,
        };
        this.state.status = 'loaded';
      } else if (!signal.aborted) {
        const errorStatus = this.resolveErrorStatus(error);
        this.state.status = errorStatus;
        logger.error('Failed to poll element instance history search', error);
        if (errorStatus === 'error-permissions') {
          this.stopPolling();
        }
      }
    } finally {
      this.pollAbortController = null;
    }
  };

  private cancelInFlightSearch = () => {
    if (this.searchAbortController !== null) {
      this.searchAbortController.abort();
      this.searchAbortController = null;
    }
  };

  private buildPayload = (processInstanceKey: string, from: number) => {
    const pattern = escapeLikePattern(this.state.searchText);
    return {
      filter: {
        processInstanceKey,
        $or: [{elementName: {$like: pattern}}, {elementId: {$like: pattern}}],
      },
      page: {limit: PAGE_SIZE * 2, from},
      sort: [{field: 'startDate' as const, order: 'asc' as const}],
    };
  };

  private resolveErrorStatus = (
    error?: RequestError | null,
  ): 'error' | 'error-permissions' => {
    if (
      error?.variant === 'failed-response' &&
      error.response.status === HTTP_STATUS_FORBIDDEN
    ) {
      return 'error-permissions';
    }
    return 'error';
  };

  override reset() {
    super.reset();
    if (this.debounceTimerId !== null) {
      clearTimeout(this.debounceTimerId);
      this.debounceTimerId = null;
    }
    this.cancelInFlightSearch();
    this.stopPolling();
    this.state = {...DEFAULT_STATE};
  }
}

const elementInstanceHistorySearchStore = new ElementInstanceHistorySearch();

export {elementInstanceHistorySearchStore};
