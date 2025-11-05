/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {IncidentErrorType} from '@camunda/camunda-api-zod-schemas/8.8';
import {makeAutoObservable} from 'mobx';
import {tracking} from 'modules/tracking';

type State = {
  selectedElementInstance: {
    elementInstanceKey: string;
    elementName: string;
  } | null;
  selectedErrorTypes: IncidentErrorType[];
  isPanelVisible: boolean;
};

const DEFAULT_STATE: State = {
  selectedElementInstance: null,
  selectedErrorTypes: [],
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
      this.state.selectedErrorTypes.length > 0
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

  clearSelection() {
    this.state.selectedElementInstance = null;
    this.state.selectedErrorTypes = [];
  }
}

export const incidentsPanelStore = new IncidentsPanel();
