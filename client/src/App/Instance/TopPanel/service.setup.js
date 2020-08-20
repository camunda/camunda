/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const getFlowNodeIdToFlowNodeInstanceMap = () => {
  const map = new Map();

  map.set(
    'start',
    new Map().set('2251799813685609', {
      activityId: 'start',
      children: [],
      endDate: '2020-08-20T10:06:33.808+0000',
      id: '2251799813685609',
      parentId: '2251799813685607',
      startDate: '2020-08-20T10:06:33.751+0000',
      state: 'COMPLETED',
      type: 'START_EVENT',
    })
  );
  map.set(
    'neverFails',
    new Map().set('2251799813685614', {
      activityId: 'neverFails',
      children: [],
      endDate: null,
      id: '2251799813685614',
      parentId: '2251799813685607',
      startDate: '2020-08-20T10:06:33.862+0000',
      state: 'ACTIVE',
      type: 'SERVICE_TASK',
    })
  );

  return map;
};

const flowNodeIdToFlowNodeInstanceMap = getFlowNodeIdToFlowNodeInstanceMap();
const events = [
  {
    id: '2251799813685607_2251799813685609',
    workflowId: '2251799813685605',
    workflowInstanceId: '2251799813685607',
    bpmnProcessId: 'withoutIncidentsProcess',
    activityId: 'start',
    activityInstanceId: '2251799813685609',
    eventSourceType: 'WORKFLOW_INSTANCE',
    eventType: 'ELEMENT_COMPLETED',
    dateTime: '2020-08-20T10:06:33.808+0000',
    metadata: null,
  },
  {
    id: '2251799813685607_2251799813685614',
    workflowId: '2251799813685605',
    workflowInstanceId: '2251799813685607',
    bpmnProcessId: 'withoutIncidentsProcess',
    activityId: 'neverFails',
    activityInstanceId: '2251799813685614',
    eventSourceType: 'JOB',
    eventType: 'TIMED_OUT',
    dateTime: '2020-08-20T10:07:13.209+0000',
    metadata: {
      jobType: 'neverFails',
      jobRetries: 3,
      jobWorker: 'operate',
      jobDeadline: '2020-08-20T10:06:46.053+0000',
      jobCustomHeaders: {},
      incidentErrorType: null,
      incidentErrorMessage: null,
      jobId: '2251799813685618',
    },
  },
];

export {flowNodeIdToFlowNodeInstanceMap, events};
