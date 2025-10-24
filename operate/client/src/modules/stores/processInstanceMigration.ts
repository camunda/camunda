/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeAutoObservable} from 'mobx';
import type {BatchOperationQuery} from 'modules/api/processInstances/operations';

const STEPS = {
  elementMapping: {
    stepNumber: 1,
    stepDescription: 'mapping elements',
  },
  summary: {
    stepNumber: 2,
    stepDescription: 'confirm',
  },
};

type State = {
  currentStep: 'elementMapping' | 'summary' | null;
  elementMapping: {[sourceId: string]: string};
  selectedSourceElementId?: string;
  selectedTargetElementId?: string;
  selectedInstancesCount: number;
  batchOperationQuery: BatchOperationQuery | null;
  sourceProcessDefinitionKey?: string | null;
  targetProcessDefinitionKey: string | null;
  hasPendingRequest: boolean;
};

const DEFAULT_STATE: State = {
  currentStep: null,
  elementMapping: {},
  selectedSourceElementId: undefined,
  selectedTargetElementId: undefined,
  selectedInstancesCount: 0,
  batchOperationQuery: null,
  targetProcessDefinitionKey: null,
  sourceProcessDefinitionKey: null,
  hasPendingRequest: false,
};

class ProcessInstanceMigration {
  state: State = {...DEFAULT_STATE};

  constructor() {
    makeAutoObservable(this);
  }

  setCurrentStep = (step: State['currentStep']) => {
    this.state.currentStep = step;
  };

  get currentStep() {
    if (this.state.currentStep === null) {
      return null;
    }

    return STEPS[this.state.currentStep];
  }

  selectSourceFlowNode = (elementId?: string) => {
    this.state.selectedTargetElementId = undefined;

    if (this.state.selectedSourceElementId === elementId) {
      this.state.selectedSourceElementId = undefined;
    } else {
      this.state.selectedSourceElementId = elementId;
    }
  };

  selectTargetElement = (elementId?: string) => {
    this.state.selectedSourceElementId = undefined;

    if (this.state.selectedTargetElementId === elementId) {
      this.state.selectedTargetElementId = undefined;
    } else {
      this.state.selectedTargetElementId = elementId;
    }
  };

  getAllSourceElements = (targetFlowNodeId: string) => {
    return Object.entries(this.state.elementMapping)
      .filter(([_, t]) => t === targetFlowNodeId)
      .map(([s, _]) => {
        return s;
      });
  };

  get selectedSourceElementIds() {
    const {selectedSourceElementId, selectedTargetElementId} = this.state;

    if (selectedSourceElementId !== undefined) {
      const targetFlowNodeId =
        this.state.elementMapping[selectedSourceElementId];

      if (targetFlowNodeId !== undefined) {
        return this.getAllSourceElements(targetFlowNodeId);
      }

      return [selectedSourceElementId];
    } else if (selectedTargetElementId !== undefined) {
      return this.getAllSourceElements(selectedTargetElementId);
    }
    return undefined;
  }

  get selectedTargetElementId() {
    const {selectedTargetElementId, selectedSourceElementId} = this.state;
    if (selectedTargetElementId !== undefined) {
      return selectedTargetElementId;
    } else if (selectedSourceElementId !== undefined) {
      return this.state.elementMapping[selectedSourceElementId];
    }
    return undefined;
  }

  enable = () => {
    this.state.currentStep = 'elementMapping';
  };

  disable = () => {
    this.state.currentStep = null;
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };

  get isSummaryStep() {
    return this.state.currentStep === 'summary';
  }

  get isEnabled() {
    return this.state.currentStep !== null;
  }

  get hasElementMapping() {
    return Object.keys(this.state.elementMapping).length > 0;
  }

  setSelectedInstancesCount = (selectedInstancesCount: number) => {
    this.state.selectedInstancesCount = selectedInstancesCount;
  };

  setBatchOperationQuery = (query: BatchOperationQuery) => {
    this.state.batchOperationQuery = query;
  };

  updateElementMapping = ({
    sourceId,
    targetId,
  }: {
    sourceId: string;
    targetId?: string;
  }) => {
    if (targetId === undefined || targetId === '') {
      delete this.state.elementMapping[sourceId];
    } else {
      this.state.elementMapping[sourceId] = targetId;
    }
  };

  clearElementMapping = () => {
    this.state.elementMapping = {};
  };

  setTargetProcessDefinitionKey = (
    key: State['targetProcessDefinitionKey'],
  ) => {
    this.state.targetProcessDefinitionKey = key;
  };

  setSourceProcessDefinitionKey = (
    key: State['sourceProcessDefinitionKey'],
  ) => {
    this.state.sourceProcessDefinitionKey = key;
  };

  resetElementMapping = () => {
    this.state.elementMapping = {...DEFAULT_STATE.elementMapping};
  };
}

export const processInstanceMigrationStore = new ProcessInstanceMigration();
