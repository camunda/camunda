/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export const DIRECTION = {
  UP: 'UP',
  DOWN: 'DOWN',
  RIGHT: 'RIGHT',
  LEFT: 'LEFT',
} as const;

export const PANEL_POSITION = {
  LEFT: 'LEFT',
  RIGHT: 'RIGHT',
} as const;

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
  MULTI_INSTANCE_BODY: 'MULTI_INSTANCE_BODY',
} as const;

export const MULTI_INSTANCE_TYPE = {
  PARALLEL: 'MULTI_PARALLEL',
  SEQUENTIAL: 'MULTI_SEQUENTIAL',
};

export const FLOWNODE_TYPE_HANDLE = {
  'bpmn:StartEvent': TYPE.EVENT_START,
  'bpmn:EndEvent': TYPE.EVENT_END,
  'bpmn:IntermediateCatchEvent': TYPE.EVENT_INTERMEDIATE_CATCH,
  'bpmn:IntermediateThrowEvent': TYPE.EVENT_INTERMEDIATE_THROW,
  'bpmn:MessageEventDefinition': TYPE.EVENT_MESSAGE,
  'bpmn:ErrorEventDefinition': TYPE.EVENT_ERROR,
  'bpmn:TimerEventDefinition': TYPE.EVENT_TIMER,
  'bpmn:EventBasedGateway': TYPE.GATEWAY_EVENT_BASED,
  'bpmn:ParallelGateway': TYPE.GATEWAY_PARALLEL,
  'bpmn:ExclusiveGateway': TYPE.GATEWAY_EXCLUSIVE,
  'bpmn:SubProcess': TYPE.TASK_SUBPROCESS,
  'bpmn:ServiceTask': TYPE.TASK_SERVICE,
  'bpmn:ReceiveTask': TYPE.TASK_RECEIVE,
  'bpmn:SendTask': TYPE.TASK_SEND,
  'bpmn:CallActivity': TYPE.TASK_CALL_ACTIVITY,
};

export const FILTER_TYPES = {
  RUNNING: 'running',
  FINISHED: 'finished',
};

export const FILTER_SELECTION = {
  running: {
    active: true,
    incidents: true,
  },
  incidents: {
    active: false,
    incidents: true,
  },
  active: {
    active: true,
    incidents: false,
  },
  finished: {
    completed: true,
    canceled: true,
  },
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
  batchOperationId: '',
  variable: {name: '', value: ''},
};

export const DEFAULT_FILTER = FILTER_SELECTION.running;

export const INSTANCES_LABELS = {
  running: 'Running Instances',
  active: 'Active',
  incidents: 'Incidents',
  finished: 'Finished Instances',
  completed: 'Completed',
  canceled: 'Canceled',
};

export const STATE = {
  ACTIVE: 'ACTIVE',
  COMPLETED: 'COMPLETED',
  CANCELED: 'CANCELED',
  INCIDENT: 'INCIDENT',
  TERMINATED: 'TERMINATED',
};

export const SORT_ORDER = {
  ASC: 'asc',
  DESC: 'desc',
} as const;

export const DEFAULT_SORTING: {
  sortBy: string;
  sortOrder: 'desc' | 'asc';
} = {
  sortBy: 'workflowName',
  sortOrder: SORT_ORDER.DESC,
};

export const PANE_ID = {
  TOP: 'TOP',
  BOTTOM: 'BOTTOM',
  LEFT: 'LEFT',
  RIGHT: 'RIGHT',
};

export const EXPAND_STATE = {
  DEFAULT: 'DEFAULT',
  EXPANDED: 'EXPANDED',
  COLLAPSED: 'COLLAPSED',
} as const;

export const FLOW_NODE_STATE_OVERLAY_ID = 'flow-node-state';
export const STATISTICS_OVERLAY_ID = 'flow-nodes-statistics';

export const DROPDOWN_PLACEMENT = {
  TOP: 'top',
  BOTTOM: 'bottom',
};

export const OPERATION_TYPE = {
  RESOLVE_INCIDENT: 'RESOLVE_INCIDENT',
  CANCEL_WORKFLOW_INSTANCE: 'CANCEL_WORKFLOW_INSTANCE',
  UPDATE_VARIABLE: 'UPDATE_VARIABLE',
} as const;

export const BADGE_TYPE = {
  RUNNING_INSTANCES: 'RUNNING_INSTANCES',
  FILTERS: 'FILTERS',
  INCIDENTS: 'INCIDENTS',
  SELECTIONS: 'SELECTIONS',
} as const;

export const OPERATION_STATE = {
  SCHEDULED: 'SCHEDULED',
  LOCKED: 'LOCKED',
  SENT: 'SENT',
  COMPLETED: 'COMPLETED',
};

export const ACTIVE_OPERATION_STATES = [
  OPERATION_STATE.SCHEDULED,
  OPERATION_STATE.LOCKED,
  OPERATION_STATE.SENT,
];

export const PAGE_TITLE = {
  LOGIN: 'Camunda Operate: Log In',
  DASHBOARD: 'Camunda Operate: Dashboard',
  INSTANCES: 'Camunda Operate: Instances',
  INSTANCE: (instanceId: string, workflowName: string) =>
    `Camunda Operate: Instance ${instanceId} of Workflow ${workflowName}`,
};

export const PILL_TYPE = {
  TIMESTAMP: 'TIMESTAMP',
  FILTER: 'FILTER',
} as const;

export const POPOVER_SIDE = {
  TOP: 'TOP',
  RIGHT: 'RIGHT',
  BOTTOM: 'BOTTOM',
  LEFT: 'LEFT',
  BOTTOM_MIRROR: 'BOTTOM_MIRROR',
};

export const INCIDENTS_BAR_HEIGHT = 42;

export const INSTANCE_SELECTION_MODE = {
  INCLUDE: 'INCLUDE',
  EXCLUDE: 'EXCLUDE',
  ALL: 'ALL',
} as const;
