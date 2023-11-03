/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {makeAutoObservable} from 'mobx';

const STEPS = {
  elementMapping: {
    stepNumber: 1,
    stepDescription: 'Mapping elements',
  },
  summary: {
    stepNumber: 2,
    stepDescription: 'Confirm',
  },
};

type State = {
  currentStep: 'elementMapping' | 'summary' | null;
};

const DEFAULT_STATE: State = {
  currentStep: null,
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

  enable = () => {
    this.state.currentStep = 'elementMapping';
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
}

export const processInstanceMigrationStore = new ProcessInstanceMigration();
