/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeAutoObservable} from 'mobx';
import {
  fetchProcessInstanceListeners,
  ListenerPayload,
  ListenersDto,
} from 'modules/api/processInstances/fetchProcessInstanceListeners';

type FetchType = 'initial' | 'prev' | 'next';

type State = {
  listenersCount: number;
  listeners: ListenersDto['listeners'];
  page: number;
  latestFetch: {
    fetchType: FetchType | null;
    itemsCount: number;
  };
  status:
    | 'initial'
    | 'first-fetch'
    | 'fetching'
    | 'fetching-next'
    | 'fetching-prev'
    | 'fetched'
    | 'error';
  currentProcessInstanceId: ProcessInstanceEntity['id'];
  currentFlowNodeId: string;
};

const DEFAULT_STATE: State = {
  listenersCount: 0,
  listeners: [],
  page: 1,
  latestFetch: {fetchType: null, itemsCount: 0},
  status: 'initial',
  currentProcessInstanceId: '',
  currentFlowNodeId: '',
};

const MAX_LISTENERS_STORED = 200;
const MAX_LISTENERS_PER_REQUEST = 50;

const DEFAULT_PAYLOAD: ListenerPayload = {
  flowNodeId: '',
  pageSize: MAX_LISTENERS_PER_REQUEST,
  searchAfter: [],
  searchBefore: [],
};

class ProcessInstanceListeners {
  state: State = {...DEFAULT_STATE};

  constructor() {
    makeAutoObservable(this);
  }

  startFetching = () => {
    if (this.state.status === 'initial') {
      this.state.status = 'first-fetch';
    } else {
      this.state.status = 'fetching';
    }
  };

  startFetchingNext = () => {
    this.state.status = 'fetching-next';
  };

  startFetchingPrev = () => {
    this.state.status = 'fetching-prev';
  };

  handleFetchSuccess = () => {
    this.state.status = 'fetched';
  };

  handleFetchFailure = () => {
    this.state.status = 'error';
    this.state.listenersCount = 0;
    this.state.listeners = [];
  };

  setListeners = ({
    listenersCount,
    listeners,
  }: {
    listenersCount: number;
    listeners: ListenerEntity[];
  }) => {
    this.state.listeners = listeners;
    this.state.listenersCount = listenersCount;
  };

  setLatestFetchDetails = (fetchType: FetchType, listenersCount: number) => {
    this.state.latestFetch.fetchType = fetchType;
    this.state.latestFetch.itemsCount = listenersCount;
  };

  getListenersFailureCount = () => {
    if (!this.state.listenersCount) return 0;

    const failedListeners = this.state?.listeners?.filter(
      (listener) => listener.state === 'FAILED',
    );

    return failedListeners?.length || 0;
  };

  getListeners = (fetchType: FetchType, listeners: ListenerEntity[]) => {
    switch (fetchType) {
      case 'next':
        const allListeners = [...this.state?.listeners, ...listeners];

        return allListeners?.slice(
          Math.max(allListeners?.length - MAX_LISTENERS_STORED, 0),
        );
      case 'prev':
        return [...listeners, ...this.state?.listeners].slice(
          0,
          MAX_LISTENERS_STORED,
        );
      case 'initial':
      default:
        return listeners;
    }
  };

  shouldFetchPreviousListeners = () => {
    const {latestFetch, listeners, status} = this.state;
    if (
      ['fetching-prev', 'fetching-next', 'fetching'].includes(status) ||
      listeners.length < MAX_LISTENERS_PER_REQUEST
    ) {
      return false;
    }

    return (
      (latestFetch?.fetchType === 'next' &&
        latestFetch.itemsCount === MAX_LISTENERS_PER_REQUEST) ||
      (latestFetch?.fetchType === 'prev' &&
        listeners?.length === MAX_LISTENERS_STORED)
    );
  };

  shouldFetchNextListeners = () => {
    const {latestFetch, listeners, status} = this.state;
    if (
      ['fetching-prev', 'fetching-next', 'fetching'].includes(status) ||
      listeners.length < MAX_LISTENERS_PER_REQUEST
    ) {
      return false;
    }

    return (
      (latestFetch.fetchType === 'next' &&
        latestFetch.itemsCount === MAX_LISTENERS_PER_REQUEST) ||
      (latestFetch.fetchType === 'prev' &&
        listeners.length === MAX_LISTENERS_STORED) ||
      latestFetch.fetchType === 'initial'
    );
  };

  fetchPreviousInstances = async () => {
    this.startFetchingPrev();

    return this.fetchListeners({
      fetchType: 'prev',
      processInstanceId: this.state.currentProcessInstanceId,
      payload: {
        flowNodeId: this.state.currentFlowNodeId,
        searchBefore: this.state.listeners[0]?.sortValues,
        pageSize: MAX_LISTENERS_PER_REQUEST,
      },
    });
  };

  fetchNextInstances = async () => {
    this.startFetchingNext();

    return this.fetchListeners({
      fetchType: 'next',
      processInstanceId: this.state.currentProcessInstanceId,
      payload: {
        flowNodeId: this.state.currentFlowNodeId,
        searchAfter:
          this.state.listeners[this.state.listeners.length - 1]?.sortValues,
        pageSize: MAX_LISTENERS_PER_REQUEST,
      },
    });
  };

  fetchListeners = async ({
    fetchType,
    processInstanceId,
    payload = DEFAULT_PAYLOAD,
  }: {
    fetchType: FetchType;
    processInstanceId: ProcessInstanceEntity['id'];
    payload: ListenerPayload;
  }) => {
    this.state.currentProcessInstanceId = processInstanceId;
    this.state.currentFlowNodeId = payload.flowNodeId;

    if (fetchType === 'initial') {
      this.startFetching();
    }

    if (!payload.flowNodeId) {
      payload.flowNodeId = DEFAULT_PAYLOAD.flowNodeId;
    }

    if (!payload.pageSize) {
      payload.pageSize = DEFAULT_PAYLOAD.pageSize;
    }

    const response = await fetchProcessInstanceListeners({
      processInstanceId,
      payload,
    });

    if (response.isSuccess) {
      const {listeners, totalCount} = response.data;

      this.setListeners({
        listenersCount: totalCount,
        listeners: this.getListeners(fetchType, listeners),
      });

      this.setLatestFetchDetails(fetchType, listeners?.length);

      this.handleFetchSuccess();
    } else {
      this.handleFetchFailure();
    }
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const processInstanceListenersStore = new ProcessInstanceListeners();
export {MAX_LISTENERS_STORED};
