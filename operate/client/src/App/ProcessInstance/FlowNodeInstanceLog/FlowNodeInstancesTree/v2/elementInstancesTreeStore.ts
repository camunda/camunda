/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {makeAutoObservable} from 'mobx';
import {searchElementInstances} from 'modules/api/v2/elementInstances/searchElementInstances';
import {logger} from 'modules/logger';

const PAGE_SIZE = 50;

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

class ElementInstancesTreeStore {
  state: State = {
    rootScopeKey: null,
    nodes: new Map(),
    expandedNodes: new Set(),
    abortControllers: new Map(),
  };

  constructor() {
    makeAutoObservable(this);
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

  setRootNode = async (processInstanceKey: string) => {
    if (this.state.rootScopeKey === processInstanceKey) {
      return;
    }

    this.state.abortControllers.forEach((controller) => {
      controller.abort();
    });
    this.state.abortControllers.clear();

    this.state.nodes.clear();
    this.state.expandedNodes.clear();
    this.state.rootScopeKey = processInstanceKey;

    this.state.expandedNodes.add(processInstanceKey);

    await this.fetchFirstPage(processInstanceKey);
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
      this.state.nodes.set(scopeKey, {
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
      this.state.nodes.set(scopeKey, {
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
      this.state.nodes.set(scopeKey, {
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
      this.state.nodes.set(scopeKey, {
        ...nodeData,
        status,
      });
    } else {
      this.state.nodes.set(scopeKey, {
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

  reset = () => {
    this.state.abortControllers.forEach((controller) => {
      controller.abort();
    });
    this.state.abortControllers.clear();

    this.state.rootScopeKey = null;
    this.state.nodes.clear();
    this.state.expandedNodes.clear();
  };
}

const elementInstancesTreeStore = new ElementInstancesTreeStore();
export {elementInstancesTreeStore};
