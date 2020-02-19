/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export const LOADING_STATE = {
  OPERATION_SCHEDULED: 'OPERATION_SCHEDULED',
  LOAD_FAILED: 'LOAD_FAILED',
  LOADING: 'LOADING',
  LOADED: 'LOADED'
};

export const SUBSCRIPTION_TOPIC = {
  LOAD_STATE_STATISTICS: 'LOAD_STATE_STATISTICS',
  LOAD_CORE_STATS: 'LOAD_CORE_STATS',
  LOAD_STATE_DEFINITIONS: 'LOAD_STATE_DEFINITIONS',
  LOAD_LIST_INSTANCES: 'LOAD_LIST_INSTANCES',
  LOAD_SELECTION_INSTANCES: 'LOAD_SELECTION_INSTANCES',
  REFRESH_AFTER_OPERATION: 'REFRESH_AFTER_OPERATION',
  CONSTANT_REFRESH: 'CONSTANT_REFRESH',
  REFRESH_SELECTION: 'REFRESH_SELECTION',
  LOAD_NEW_SELECTION: 'LOAD_NEW_SELECTION',
  LOAD_UPDATE_SELECTION: 'LOAD_UPDATE_SELECTION',
  LOAD_INCIDENTS: 'LOAD_INCIDENTS',
  LOAD_EVENTS: 'LOAD_EVENTS',
  LOAD_INSTANCE_TREE: 'LOAD_INSTANCE_TREE',
  LOAD_VARIABLES: 'LOAD_VARIABLES',
  LOAD_INSTANCE: 'LOAD_INSTANCE',
  LOAD_INSTANCES_BY_WORKFLOW: 'LOAD_INSTANCES_BY_WORKFLOW',
  LOAD_INCIDENTS_BY_ERROR: 'LOAD_INCIDENTS_BY_ERROR',
  LOAD_BATCH_OPERATIONS: 'LOAD_BATCH_OPERATIONS',
  CREATE_BATCH_OPERATION: 'CREATE_BATCH_OPERATION'
};

export const DIRECTION = {
  UP: 'UP',
  DOWN: 'DOWN',
  RIGHT: 'RIGHT',
  LEFT: 'LEFT'
};

export const PANEL_POSITION = {
  LEFT: 'LEFT',
  RIGHT: 'RIGHT'
};

export const FLOWNODE_TYPE = {
  TASK: 'Task',
  GATEWAY: 'Gateway',
  EVENT: 'Event'
};

export const TYPE = {
  GATEWAY_EXCLUSIVE: 'GATEWAY_EXCLUSIVE',
  GATEWAY_EVENT_BASED: 'GATEWAY_EVENT_BASED',
  GATEWAY_PARALLEL: 'GATEWAY_PARALLEL',
  EVENT_START: 'START',
  EVENT_END: 'END',
  EVENT_MESSAGE: 'EVENT_MESSAGE',
  EVENT_TIMER: 'EVENT_TIMER',
  EVENT_ERROR: 'EVENT_ERROR',
  EVENT_INTERMEDIATE_CATCH: 'INTERMEDIATE_CATCH',
  EVENT_INTERMEDIATE_THROW: 'INTERMEDIATE_THROW',
  EVENT_BOUNDARY_INTERRUPTING: 'BOUNDARY_INTERRUPTING',
  EVENT_BOUNDARY_NON_INTERRUPTING: 'BOUNDARY_NON_INTERRUPTING',

  TASK_DEFAULT: 'TASK_DEFAULT',
  TASK_SERVICE: 'TASK_SERVICE',
  TASK_RECEIVE: 'TASK_RECEIVE',
  TASK_SEND: 'TASK_SEND',
  TASK_SUBPROCESS: 'TASK_SUBPROCESS',
  TASK_CALL_ACTIVITY: 'TASK_CALL_ACTIVITY',

  EVENT_SUBPROCESS: 'EVENT_SUBPROCESS',

  WORKFLOW: 'WORKFLOW',
  MULTI_INSTANCE_BODY: 'MULTI_INSTANCE_BODY'
};

export const MULTI_INSTANCE_TYPE = {
  PARALLEL: 'MULTI_PARALLEL',
  SEQUENTIAL: 'MULTI_SEQUENTIAL'
};

export const FLOWNODE_TYPE_HANDLE = {
  'bpmn:StartEvent': TYPE.EVENT_START,
  'bpmn:EndEvent': TYPE.EVENT_END,
  'bpmn:IntermediateCatchEvent': TYPE.EVENT_INTERMEDIATE_CATCH,
  'bpmn:IntermediateThrowEvent': TYPE.EVENT_INTERMEDIATE_THROW,
  'bpmn:MessageEventDefinition': TYPE.EVENT_MESSAGE,
  'bpmn:ErrorEventDefinition': TYPE.EVENT_ERROR,
  'bpmn:TimerEventDefinition': TYPE.EVENT_TIMER,
  'bpmn:BoundaryEvent': TYPE.EVENT_BOUNDARY,
  'bpmn:EventBasedGateway': TYPE.GATEWAY_EVENT_BASED,
  'bpmn:ParallelGateway': TYPE.GATEWAY_PARALLEL,
  'bpmn:ExclusiveGateway': TYPE.GATEWAY_EXCLUSIVE,
  'bpmn:SubProcess': TYPE.TASK_SUBPROCESS,
  'bpmn:ServiceTask': TYPE.TASK_SERVICE,
  'bpmn:ReceiveTask': TYPE.TASK_RECEIVE,
  'bpmn:SendTask': TYPE.TASK_SEND,
  'bpmn:CallActivity': TYPE.TASK_CALL_ACTIVITY
};

export const FILTER_TYPES = {
  RUNNING: 'running',
  FINISHED: 'finished'
};

export const FILTER_SELECTION = {
  running: {
    active: true,
    incidents: true
  },
  incidents: {
    active: false,
    incidents: true
  },
  active: {
    active: true,
    incidents: false
  },
  finished: {
    completed: true,
    canceled: true
  }
};

// values that we read from the url and prefill the inputs
export const DEFAULT_FILTER_CONTROLLED_VALUES = {
  active: false,
  incidents: false,
  completed: false,
  canceled: false,
  ids: '',
  errorMessage: '',
  startDate: '',
  endDate: '',
  activityId: '',
  version: '',
  workflow: '',
  variable: {name: '', value: ''}
};

export const DEFAULT_FILTER = FILTER_SELECTION.running;

export const INCIDENTS_FILTER = FILTER_SELECTION.incidents;

export const INSTANCES_LABELS = {
  running: 'Running Instances',
  active: 'Active',
  incidents: 'Incidents',
  finished: 'Finished Instances',
  completed: 'Completed',
  canceled: 'Canceled'
};

export const CONTEXTUAL_MESSAGE_TYPE = {
  DROP_SELECTION: 'DROP_SELECTION'
};

export const STATE = {
  ACTIVE: 'ACTIVE',
  COMPLETED: 'COMPLETED',
  CANCELED: 'CANCELED',
  INCIDENT: 'INCIDENT',
  TERMINATED: 'TERMINATED'
};

export const SORT_ORDER = {
  ASC: 'asc',
  DESC: 'desc'
};

export const DEFAULT_SORTING = {
  sortBy: 'workflowName',
  sortOrder: SORT_ORDER.DESC
};

export const DEFAULT_FIRST_ELEMENT = 0;

export const DEFAULT_MAX_RESULTS = 50;
export const DEFAULT_SELECTED_INSTANCES = {all: false, ids: [], excludeIds: []};
export const VISIBLE_INSTANCES_IN_SELECTION = 10;

export const PANE_ID = {
  TOP: 'TOP',
  BOTTOM: 'BOTTOM',
  LEFT: 'LEFT',
  RIGHT: 'RIGHT'
};

export const EXPAND_STATE = {
  DEFAULT: 'DEFAULT',
  EXPANDED: 'EXPANDED',
  COLLAPSED: 'COLLAPSED'
};

export const UNNAMED_ACTIVITY = 'Unnamed Activity';

export const FLOW_NODE_STATE_OVERLAY_ID = 'flow-node-state';
export const STATISTICS_OVERLAY_ID = 'flow-nodes-statistics';

export const DROPDOWN_PLACEMENT = {
  TOP: 'top',
  BOTTOM: 'bottom'
};

export const MESSAGES_TYPE = {
  DROP_SELECTION: 'DROP_SELECTION'
};

export const EVENT_TYPE = {
  CREATED: 'CREATED'
};

export const EVENT_SOURCE_TYPE = {
  INCIDENT: 'INCIDENT'
};

export const OPERATION_TYPE = {
  RESOLVE_INCIDENT: 'RESOLVE_INCIDENT',
  CANCEL_WORKFLOW_INSTANCE: 'CANCEL_WORKFLOW_INSTANCE',
  UPDATE_VARIABLE: 'UPDATE_VARIABLE'
};

export const BADGE_TYPE = {
  RUNNING_INSTANCES: 'RUNNING_INSTANCES',
  FILTERS: 'FILTERS',
  INCIDENTS: 'INCIDENTS',
  SELECTIONS: 'SELECTIONS'
};

export const OPERATION_STATE = {
  SCHEDULED: 'SCHEDULED',
  LOCKED: 'LOCKED',
  SENT: 'SENT',
  FAILED: 'FAILED',
  COMPLETED: 'COMPLETED'
};

export const ACTIVE_OPERATION_STATES = [
  OPERATION_STATE.SCHEDULED,
  OPERATION_STATE.LOCKED,
  OPERATION_STATE.SENT
];

export const PAGE_TITLE = {
  LOGIN: 'Camunda Operate: Log In',
  DASHBOARD: 'Camunda Operate: Dashboard',
  INSTANCES: 'Camunda Operate: Instances',
  INSTANCE: (instanceId, workflowName) =>
    `Camunda Operate: Instance ${instanceId} of Workflow ${workflowName}`
};

export const PILL_TYPE = {
  TIMESTAMP: 'TIMESTAMP',
  FILTER: 'FILTER'
};

export const POPOVER_SIDE = {
  TOP: 'TOP',
  RIGHT: 'RIGHT',
  BOTTOM: 'BOTTOM',
  LEFT: 'LEFT',
  BOTTOM_MIRROR: 'BOTTOM_MIRROR'
};

export const INCIDENTS_BAR_HEIGHT = 42;

export const POLL_TOPICS = {
  INSTANCE: 'INSTANCE',
  INSTANCES_LIST: 'INSTANCES_LIST',
  OPERATIONS: 'OPERATIONS'
};
