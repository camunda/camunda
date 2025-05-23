/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

type Process = {
  id: string;
  name: string;
  bpmnProcessId: string;
  version: number;
  startEventFormId: string | null;
  sortValues?: string[];
  bpmnXml?: string;
};

const mockProcesses: Process[] = [
  {
    id: '2251799813685285',
    name: 'multipleVersions',
    bpmnProcessId: 'multipleVersions',
    version: 1,
    startEventFormId: null,
  },
  {
    id: '2251799813685271',
    name: 'Order process',
    bpmnProcessId: 'orderProcess',
    version: 1,
    startEventFormId: null,
  },
];

const mockProcessWithStartForm: Process = {
  id: '2251799813685285',
  name: 'startForm',
  bpmnProcessId: 'startForm',
  version: 1,
  startEventFormId: 'startFormForm',
};

export {mockProcesses, mockProcessWithStartForm};
export type {Process};
