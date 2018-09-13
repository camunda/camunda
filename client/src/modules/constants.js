export const DIRECTION = {
  UP: 'UP',
  DOWN: 'DOWN',
  RIGHT: 'RIGHT',
  LEFT: 'LEFT'
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

export const CONTEXTUAL_MESSAGE_TYPE = {
  DROP_SELECTION: 'DROP_SELECTION'
};

export const INSTANCE_STATE = {
  ACTIVE: 'ACTIVE',
  COMPLETED: 'COMPLETED',
  CANCELED: 'CANCELED',
  INCIDENT: 'INCIDENT'
};

export const SORT_ORDER = {
  ASC: 'asc',
  DESC: 'desc'
};

export const DEFAULT_SORTING = {sortBy: 'id', sortOrder: SORT_ORDER.DESC};

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

export const FLOW_NODE_TYPE = {
  TASK: 'TASK',
  START_EVENT: 'START_EVENT',
  END_EVENT: 'END_EVENT',
  EXCLUSIVE_GATEWAY: 'EXCLUSIVE_GATEWAY',
  PARALLEL_GATEWAY: 'PARALLEL_GATEWAY'
};

export const ACTIVITY_STATE = {
  ACTIVE: 'ACTIVE',
  COMPLETED: 'COMPLETED',
  TERMINATED: 'TERMINATED',
  INCIDENT: 'INCIDENT'
};

export const BADGE_TYPE = {
  FILTERS: 'filters',
  SELECTIONS: 'selections',
  SELECTIONHEAD: 'selectionHead',
  OPENSELECTIONHEAD: 'openSelectionHead',
  COMBOSELECTION: 'comboSelection',
  INCIDENTS: 'incidents',
  INSTANCES: 'instances'
};

export const ACTION_TYPE = {
  RETRY: 'RETRY'
};

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
  UPDATE_RETRIES: 'UPDATE_RETRIES'
};
