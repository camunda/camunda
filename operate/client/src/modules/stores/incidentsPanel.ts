/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {IncidentErrorType} from '@camunda/camunda-api-zod-schemas/8.9';
import {makeAutoObservable} from 'mobx';
import {tracking} from 'modules/tracking';

type SourceFilter = 'ALL' | 'GLOBAL' | 'MODEL';

type State = {
  selectedElementInstance: {
    elementInstanceKey: string;
    elementName: string;
  } | null;
  selectedElementId: string | null;
  selectedErrorTypes: IncidentErrorType[];
  selectedSourceFilter: SourceFilter;
  isPanelVisible: boolean;
};

const DEFAULT_STATE: State = {
  selectedElementInstance: null,
  selectedElementId: null,
  selectedErrorTypes: [],
  selectedSourceFilter: 'ALL',
  isPanelVisible: false,
};

class IncidentsPanel {
  state: State = {...DEFAULT_STATE};

  constructor() {
    makeAutoObservable(this);
  }

  get hasActiveFilters(): boolean {
    return (
      this.state.selectedElementInstance !== null ||
      this.state.selectedElementId !== null ||
      this.state.selectedErrorTypes.length > 0 ||
      this.state.selectedSourceFilter !== 'ALL'
    );
  }

  showIncidentsForElementInstance(
    elementInstanceKey: string,
    elementName: string,
  ) {
    this.clearSelection();
    this.state.selectedElementInstance = {elementInstanceKey, elementName};
    this.setPanelOpen(true);
  }

  /**
   * Opens the incidents panel with a filter for the given `elementId`.
   * Prefer to use {@linkcode showIncidentsForElementInstance}! This method
   * is a fallback for cases where no element instance is available.
   */
  showIncidentsForElementId(elementId: string) {
    this.clearSelection();
    this.state.selectedElementId = elementId;
    this.setPanelOpen(true);
  }

  setPanelOpen(isOpen: boolean) {
    this.state.isPanelVisible = isOpen;

    if (isOpen) {
      tracking.track({
        eventName: 'flow-node-incident-details-opened',
      });
    }
  }

  setErrorTypeSelection(errorTypes: IncidentErrorType[]) {
    this.state.selectedErrorTypes = errorTypes;
  }

  setSourceFilter(filter: SourceFilter) {
    this.state.selectedSourceFilter = filter;
  }

  clearSelection() {
    this.state.selectedElementInstance = null;
    this.state.selectedElementId = null;
    this.state.selectedErrorTypes = [];
    this.state.selectedSourceFilter = 'ALL';
  }
}

export type {SourceFilter};
export const incidentsPanelStore = new IncidentsPanel();
