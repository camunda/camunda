/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export const DIRECTION = {
  UP: 'UP',
  DOWN: 'DOWN',
  RIGHT: 'RIGHT',
  LEFT: 'LEFT'
};

export const FLOWNODE_TYPE = {
  TASK: 'Task',
  GATEWAY: 'Gateway',
  EVENT: 'Event'
};

export const NOT_SELECTABLE_FLOWNODE_TYPES = {
  DEFINITION: 'Definitions',
  PROCESS: 'Process',
  SEQUENCEFLOW: 'SequenceFlow',
  SHAPE: 'BPMNShape',
  EDGE: 'BPMNEdge',
  DIAGRAM: 'BPMNDiagram',
  PLANE: 'BPMNPlane'
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
export const DASHBOARD_LABELS = {
  running: 'Running Instances',
  active: 'Active Instances',
  incidents: 'Instances with Incident'
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

export const COMBO_BADGE_TYPE = {
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
