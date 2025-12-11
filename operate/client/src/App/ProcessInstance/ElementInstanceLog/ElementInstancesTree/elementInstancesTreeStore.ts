/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {makeObservable, observable, action, override} from 'mobx';
import {searchElementInstances} from 'modules/api/v2/elementInstances/searchElementInstances';
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from 'modules/stores/networkReconnectionHandler';

const PAGE_SIZE = 50;
const POLLING_INTERVAL = 5000;

type PageMetadata = {
  totalItems: number;
  windowStart: number;
  windowEnd: number;
};

type NodeData = {
  items: ElementInstance[];
  pageMetadata: PageMetadata;
  status: 'idle' | 'loading' | 'error' | 'loaded';
};

type State = {
  rootScopeKey: string | null;
  nodes: Map<string, NodeData>;
  expandedNodes: Set<string>;
  abortControllers: Map<string, AbortController>;
};

class ElementInstancesTreeStore extends NetworkReconnectionHandler {
  state: State = {
    rootScopeKey: null,
    nodes: new Map(),
    expandedNodes: new Set(),
    abortControllers: new Map(),
  };

  isPollRequestRunning: boolean = false;
  intervalId: ReturnType<typeof setInterval> | null = null;
  pollAbortController: AbortController | null = null;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      setRootNode: action,
      expandNode: action,
      collapseNode: action,
      toggleNode: action,
      setNodeData: action,
      reset: override,
    });
  }

  private getOrCreateAbortController = (scopeKey: string): AbortController => {
    const existing = this.state.abortControllers.get(scopeKey);

    if (existing && !existing.signal.aborted) {
      return existing;
    }

    const controller = new AbortController();
    this.state.abortControllers.set(scopeKey, controller);
    return controller;
  };

  setRootNode = async (
    processInstanceKey: string,
    config?: {
      enablePolling?: boolean;
    },
  ) => {
    const isNewRoot = this.state.rootScopeKey !== processInstanceKey;
    const isPollingEnabled = config?.enablePolling ?? false;

    if (!isNewRoot) {
      if (isPollingEnabled && this.intervalId === null) {
        this.startPolling();
      } else if (!isPollingEnabled && this.intervalId !== null) {
        this.stopPolling();
      }
      return;
    }

    this.stopPolling();

    this.state.abortControllers.forEach((controller) => {
      controller.abort();
    });
    this.state.abortControllers.clear();

    this.state.nodes.clear();
    this.state.expandedNodes.clear();
    this.state.rootScopeKey = processInstanceKey;

    this.state.expandedNodes.add(processInstanceKey);

    await this.fetchFirstPage(processInstanceKey);

    if (isPollingEnabled) {
      this.startPolling();
    }
  };

  expandNode = async (scopeKey: string) => {
    if (this.state.expandedNodes.has(scopeKey)) {
      return;
    }

    this.state.expandedNodes.add(scopeKey);

    if (!this.state.nodes.has(scopeKey)) {
      await this.fetchFirstPage(scopeKey);
    }
  };

  private fetchFirstPage = async (scopeKey: string) => {
    this.setNodeStatus(scopeKey, 'loading');

    const controller = this.getOrCreateAbortController(scopeKey);

    const {response, error} = await searchElementInstances(
      {
        filter: {elementInstanceScopeKey: scopeKey},
        page: {limit: PAGE_SIZE * 2, from: 0},
        sort: [{field: 'startDate', order: 'asc'}],
      },
      controller.signal,
    );

    if (controller.signal.aborted) {
      return;
    }

    if (!this.state.expandedNodes.has(scopeKey)) {
      return;
    }

    if (response !== null) {
      this.setNodeData(scopeKey, {
        items: response.items,
        pageMetadata: {
          totalItems: response.page.totalItems,
          windowStart: 0,
          windowEnd: PAGE_SIZE,
        },
        status: 'loaded',
      });
    } else {
      this.setNodeStatus(scopeKey, 'error');
      logger.error('Failed to fetch element instances', error);
    }
  };

  collapseNode = (scopeKey: string) => {
    const controller = this.state.abortControllers.get(scopeKey);
    if (controller) {
      controller.abort();
      this.state.abortControllers.delete(scopeKey);
    }

    this.state.expandedNodes.delete(scopeKey);

    this.collapseAndRemoveDescendants(scopeKey);

    this.state.nodes.delete(scopeKey);
  };

  toggleNode = async (scopeKey: string) => {
    if (this.isNodeExpanded(scopeKey)) {
      this.collapseNode(scopeKey);
    } else {
      await this.expandNode(scopeKey);
    }
  };

  private collapseAndRemoveDescendants = (parentScopeKey: string) => {
    const nodeData = this.state.nodes.get(parentScopeKey);

    if (!nodeData) {
      return;
    }

    nodeData.items.forEach((item) => {
      const childKey = item.elementInstanceKey;

      const controller = this.state.abortControllers.get(childKey);
      if (controller) {
        controller.abort();
        this.state.abortControllers.delete(childKey);
      }

      if (this.state.expandedNodes.has(childKey)) {
        this.state.expandedNodes.delete(childKey);
      }

      if (this.state.nodes.has(childKey)) {
        this.collapseAndRemoveDescendants(childKey);
        this.state.nodes.delete(childKey);
      }
    });
  };

  fetchNextPage = async (scopeKey: string): Promise<number> => {
    const nodeData = this.state.nodes.get(scopeKey);

    if (!nodeData) {
      logger.error('Cannot fetch next page: node not found');
      return -1;
    }

    const nextWindowStart = nodeData.pageMetadata.windowEnd;
    const nextWindowEnd = nextWindowStart + PAGE_SIZE;

    if (nextWindowStart >= nodeData.pageMetadata.totalItems) {
      return 0;
    }

    this.setNodeStatus(scopeKey, 'loading');

    const controller = this.getOrCreateAbortController(scopeKey);

    const {response, error} = await searchElementInstances(
      {
        filter: {elementInstanceScopeKey: scopeKey},
        page: {limit: PAGE_SIZE * 2, from: nextWindowStart},
        sort: [{field: 'startDate', order: 'asc'}],
      },
      controller.signal,
    );

    if (controller.signal.aborted) {
      return -1;
    }

    if (!this.state.expandedNodes.has(scopeKey)) {
      return -1;
    }

    if (response !== null) {
      this.setNodeData(scopeKey, {
        items: response.items,
        pageMetadata: {
          totalItems: response.page.totalItems,
          windowStart: nextWindowStart,
          windowEnd: nextWindowEnd,
        },
        status: 'loaded',
      });

      return Math.max(0, response.items.length - PAGE_SIZE);
    } else {
      this.setNodeStatus(scopeKey, 'error');
      logger.error('Failed to fetch next page', error);
      return -1;
    }
  };

  fetchPreviousPage = async (scopeKey: string): Promise<number> => {
    const nodeData = this.state.nodes.get(scopeKey);

    if (!nodeData) {
      logger.error('Cannot fetch previous page: node not found');
      return -1;
    }

    const prevWindowEnd = nodeData.pageMetadata.windowStart;
    const prevWindowStart = Math.max(0, prevWindowEnd - PAGE_SIZE);

    if (nodeData.pageMetadata.windowStart === 0) {
      return 0;
    }

    this.setNodeStatus(scopeKey, 'loading');

    const controller = this.getOrCreateAbortController(scopeKey);

    const {response, error} = await searchElementInstances(
      {
        filter: {elementInstanceScopeKey: scopeKey},
        page: {limit: PAGE_SIZE * 2, from: prevWindowStart},
        sort: [{field: 'startDate', order: 'asc'}],
      },
      controller.signal,
    );

    if (controller.signal.aborted) {
      return -1;
    }

    if (!this.state.expandedNodes.has(scopeKey)) {
      return -1;
    }

    if (response !== null) {
      this.setNodeData(scopeKey, {
        items: response.items,
        pageMetadata: {
          totalItems: response.page.totalItems,
          windowStart: prevWindowStart,
          windowEnd: prevWindowEnd,
        },
        status: 'loaded',
      });

      return Math.min(PAGE_SIZE, response.items.length);
    } else {
      this.setNodeStatus(scopeKey, 'error');
      logger.error('Failed to fetch previous page', error);
      return -1;
    }
  };

  private setNodeStatus = (scopeKey: string, status: NodeData['status']) => {
    const nodeData = this.state.nodes.get(scopeKey);
    if (nodeData) {
      this.setNodeData(scopeKey, {
        ...nodeData,
        status,
      });
    } else {
      this.setNodeData(scopeKey, {
        items: [],
        pageMetadata: {
          totalItems: 0,
          windowStart: 0,
          windowEnd: 0,
        },
        status,
      });
    }
  };

  isNodeExpanded = (scopeKey: string): boolean => {
    return this.state.expandedNodes.has(scopeKey);
  };

  getItems = (scopeKey: string): ElementInstance[] => {
    return this.state.nodes.get(scopeKey)?.items ?? [];
  };

  hasNextPage = (scopeKey: string): boolean => {
    const nodeData = this.state.nodes.get(scopeKey);
    if (nodeData === undefined) {
      return false;
    }

    return nodeData.pageMetadata.windowEnd < nodeData.pageMetadata.totalItems;
  };

  hasPreviousPage = (scopeKey: string): boolean => {
    const nodeData = this.state.nodes.get(scopeKey);
    if (nodeData === undefined) {
      return false;
    }

    return nodeData.pageMetadata.windowStart > 0;
  };

  setNodeData = (scopeKey: string, newNodeData: NodeData) => {
    this.state.nodes.set(scopeKey, newNodeData);
  };

  reset() {
    super.reset();
    this.stopPolling();

    this.state.abortControllers.forEach((controller) => {
      controller.abort();
    });
    this.state.abortControllers.clear();

    this.state.rootScopeKey = null;
    this.state.nodes.clear();
    this.state.expandedNodes.clear();
  }

  private hasRunningChildren = (scopeKey: string): boolean => {
    const nodeData = this.state.nodes.get(scopeKey);
    if (!nodeData) {
      return false;
    }

    return nodeData.items.some((item) => item.state === 'ACTIVE');
  };

  pollExpandedNodes = async () => {
    if (document.visibilityState === 'hidden') {
      return;
    }

    if (!this.state.rootScopeKey || this.isPollRequestRunning) {
      return;
    }

    this.isPollRequestRunning = true;

    this.pollAbortController = new AbortController();
    const signal = this.pollAbortController.signal;

    const expandedNodesArray = Array.from(this.state.expandedNodes);

    const nodesToPoll = expandedNodesArray.filter((scopeKey) => {
      if (scopeKey === this.state.rootScopeKey) {
        return true;
      }
      return this.hasRunningChildren(scopeKey);
    });

    try {
      await Promise.all(
        nodesToPoll.map(async (scopeKey) => {
          const nodeData = this.state.nodes.get(scopeKey);
          if (!nodeData) {
            return;
          }

          const requestedWindowStart = nodeData.pageMetadata.windowStart;

          const {response, error} = await searchElementInstances(
            {
              filter: {elementInstanceScopeKey: scopeKey},
              page: {
                limit: PAGE_SIZE * 2,
                from: requestedWindowStart,
              },
              sort: [{field: 'startDate', order: 'asc'}],
            },
            signal,
          );

          if (signal.aborted || this.intervalId === null) {
            return;
          }

          const currentNodeData = this.state.nodes.get(scopeKey);
          if (!currentNodeData) {
            return;
          }

          if (
            currentNodeData.pageMetadata.windowStart !== requestedWindowStart
          ) {
            return;
          }

          if (response !== null) {
            this.setNodeData(scopeKey, {
              ...currentNodeData,
              items: response.items,
              status: 'loaded',
              pageMetadata: {
                ...currentNodeData.pageMetadata,
                totalItems: response.page.totalItems,
              },
            });
          } else {
            if (!signal.aborted) {
              this.setNodeStatus(scopeKey, 'error');
              logger.error('Failed to poll element instances', error);
            }
          }
        }),
      );
    } finally {
      this.isPollRequestRunning = false;
      this.pollAbortController = null;
    }
  };

  startPolling = () => {
    if (this.intervalId !== null || !this.state.rootScopeKey) {
      return;
    }

    this.intervalId = setInterval(() => {
      if (!this.isPollRequestRunning) {
        this.pollExpandedNodes();
      }
    }, POLLING_INTERVAL);
  };

  stopPolling = () => {
    if (this.intervalId !== null) {
      clearInterval(this.intervalId);
      this.intervalId = null;
    }

    if (this.pollAbortController !== null) {
      this.pollAbortController.abort();
      this.pollAbortController = null;
    }
  };
}

const elementInstancesTreeStore = new ElementInstancesTreeStore();
export {elementInstancesTreeStore};
