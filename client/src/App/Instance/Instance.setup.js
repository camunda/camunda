/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {STATE} from 'modules/constants';

import {
  createInstance,
  createActivities,
  createDiagramNode,
  createRawTreeNode,
  createIncident,
  createEvent,
  createEvents
} from 'modules/testUtils';

const createRawTree = () => {
  return {
    children: [
      createRawTreeNode({
        activityId: 'StartEvent1234',
        type: 'START_EVENT',
        state: STATE.COMPLETED
      }),
      createRawTreeNode({
        activityId: 'Service5678',
        type: 'SERVICE_TASK',
        state: STATE.COMPLETED
      }),
      createRawTreeNode({
        activityId: 'EndEvent1234',
        type: 'End_Event',
        state: STATE.COMPLETED
      })
    ]
  };
};

const createDiagramNodes = () => {
  return {
    StartEvent1234: createDiagramNode({
      $type: 'bpmn:StartEvent',
      name: 'Start the Process',
      $instanceOf: type => type === 'bpmn:FlowNode'
    }),
    Service5678: createDiagramNode({
      $type: 'bpmn:ServiceTask',
      name: 'Do something',
      $instanceOf: type => type === 'bpmn:FlowNode'
    }),
    EndEvent1234: createDiagramNode({
      $type: 'bpmn:EndEvent',
      name: 'End the Process',
      $instanceOf: type => type === 'bpmn:FlowNode'
    })
  };
};

const createIncidents = () => {
  return {
    count: 1,
    incidents: [
      createIncident({
        errorType: 'Condition error',
        flowNodeId: 'Service5678'
      })
    ],
    errorTypes: [
      {
        errorType: 'Condition error',
        count: 1
      }
    ],
    flowNodes: [
      {
        flowNodeId: 'Service5678',
        flowNodeName: 'Do something',
        count: 1
      }
    ]
  };
};

const noIncidents = {count: 0, incidents: [], errorTypes: [], flowNodes: []};

const activities = createActivities(createDiagramNodes());

const workflowInstanceCompleted = createInstance({
  id: '4294980768',
  state: STATE.COMPLETED,
  activities: [
    ...activities,
    {
      id: '88',
      state: 'COMPLETED',
      activityId: 'EndEvent_042s0oc',
      startDate: '2019-01-15T12:48:49.747+0000',
      endDate: '2019-01-15T12:48:49.747+0000'
    }
  ]
});
const workflowInstanceCanceled = createInstance({
  id: '4294980768',
  state: STATE.CANCELED,
  activities: [
    ...activities,
    {
      id: '88',
      state: 'CANCELED',
      activityId: 'EndEvent_042s0oc',
      startDate: '2019-01-15T12:48:49.747+0000',
      endDate: '2019-01-15T12:48:49.747+0000'
    }
  ]
});

const workflowInstance = createInstance({
  id: '4294980768',
  state: STATE.ACTIVE
});

const workflowInstanceWithIncident = createInstance({
  id: '4294980768',
  state: STATE.INCIDENT
});

const diagramData = {
  activityId: 'taskD',
  mockDefinition: {id: 'Definition1'},
  flowNodeName: 'taskD',
  treeRowId: 'activityInstanceOfTaskD',
  metaDataMock: {
    endDate: '12 Dec 2018 00:00:00',
    activityInstanceId: 'activityInstanceOfTaskD',
    jobId: '66',
    startDate: '12 Dec 2018 00:00:00',
    jobCustomHeaders: {},
    jobRetries: 3,
    jobType: 'shipArticles',
    workflowId: '1',
    workflowInstanceId: '53'
  },
  matchingTreeRowIds: [
    'firstActivityInstanceOfTaskD',
    'secondActivityInstanceOfTaskD'
  ],
  expectedMetadata: {
    isMultiRowPeterCase: true,
    instancesCount: 2
  },
  metaDataSingelRow: {
    isSingleRowPeterCase: true,
    data: {
      endDate: '12 Dec 2018 00:00:00',
      activityInstanceId: 'firstActivityInstanceOfTaskD',
      jobId: '66',
      startDate: '12 Dec 2018 00:00:00',
      jobCustomHeaders: {},
      jobRetries: 3,
      jobType: 'shipArticles',
      workflowId: '1',
      workflowInstanceId: '53'
    }
  }
};

const treeNode = createRawTreeNode({
  id: diagramData.treeRowId,
  activityId: diagramData.activityId,
  name: diagramData.flowNodeName
});

const diagramDataStructure = {
  mockEventsPeterCase: [
    createEvent({
      activityId: diagramData.activityId,
      activityInstanceId: diagramData.matchingTreeRowIds[0]
    }),
    createEvent({
      activityId: diagramData.activityId,
      activityInstanceId: diagramData.matchingTreeRowIds[1]
    })
  ],
  mockEvents: [
    createEvent({
      activityId: diagramData.activityId,
      activityInstanceId: diagramData.treeRowId
    })
  ],
  treeNode: treeNode,
  mockTree: {
    children: [treeNode]
  },
  mockTreePeterCase: {
    children: [
      createRawTreeNode({
        id: diagramData.matchingTreeRowIds[0],
        activityId: diagramData.activityId
      }),
      createRawTreeNode({
        id: diagramData.matchingTreeRowIds[1],
        activityId: diagramData.activityId
      })
    ]
  }
};

export const testData = {
  fetch: {
    onPageLoad: {
      workflowXML: '<foo />',
      workflowInstance,
      workflowInstanceWithIncident,
      workflowInstanceCompleted,
      workflowInstanceCanceled,
      diagramNodes: createDiagramNodes(),
      instanceHistoryTree: createRawTree(),
      events: createEvents(createRawTree().children),
      incidents: createIncidents(),
      noIncidents
    }
  },
  props: {
    match: {
      params: {id: workflowInstance.id},
      isExact: true,
      path: '',
      url: ''
    }
  },
  diagramDataStructure,
  diagramData
};
