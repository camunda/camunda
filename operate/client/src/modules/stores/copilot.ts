/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeAutoObservable} from 'mobx';

class CopilotStore {
  isOpen = false;
  incidentExplanationMode = false;
  analyzeInstanceMode = false;
  analyzedInstanceId: string | null = null;

  constructor() {
    makeAutoObservable(this);
  }

  toggle = () => {
    this.isOpen = !this.isOpen;
    if (!this.isOpen) {
      this.incidentExplanationMode = false;
      this.analyzeInstanceMode = false;
      this.analyzedInstanceId = null;
    }
  };

  open = () => {
    this.isOpen = true;
  };

  close = () => {
    this.isOpen = false;
    this.incidentExplanationMode = false;
    this.analyzeInstanceMode = false;
    this.analyzedInstanceId = null;
  };

  openWithIncidentExplanation = () => {
    this.isOpen = true;
    this.incidentExplanationMode = true;
  };

  clearIncidentExplanationMode = () => {
    this.incidentExplanationMode = false;
  };

  openWithInstanceAnalysis = (instanceId: string) => {
    this.isOpen = true;
    this.analyzeInstanceMode = true;
    this.analyzedInstanceId = instanceId;
  };

  clearAnalyzeInstanceMode = () => {
    this.analyzeInstanceMode = false;
    this.analyzedInstanceId = null;
  };
}

const copilotStore = new CopilotStore();

export {copilotStore};
