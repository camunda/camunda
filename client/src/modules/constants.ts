/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const OPERATION_STATE = {
  SCHEDULED: 'SCHEDULED',
  LOCKED: 'LOCKED',
  SENT: 'SENT',
  COMPLETED: 'COMPLETED',
};

const ACTIVE_OPERATION_STATES = [
  OPERATION_STATE.SCHEDULED,
  OPERATION_STATE.LOCKED,
  OPERATION_STATE.SENT,
];

const NON_APPENDABLE_FLOW_NODES = ['bpmn:StartEvent', 'bpmn:BoundaryEvent'];

const SORT_ORDER = {
  ASC: 'asc',
  DESC: 'desc',
} as const;

const PERMISSIONS: ResourceBasedPermissionDto[] = [
  'READ',
  'DELETE',
  'UPDATE_PROCESS_INSTANCE',
  'DELETE_PROCESS_INSTANCE',
];

const FLOW_NODE_STATE_OVERLAY_ID = 'flow-node-state';
const STATISTICS_OVERLAY_ID = 'flow-nodes-statistics';

const PAGE_TITLE = {
  LOGIN: 'Operate: Log In',
  DASHBOARD: 'Operate: Dashboard',
  INSTANCES: 'Operate: Process Instances',
  INSTANCE: (instanceId: string, processName: string) =>
    `Operate: Process Instance ${instanceId} of ${processName}`,
  DECISION_INSTANCES: 'Operate: Decision Instances',
  DECISION_INSTANCE: (id: string, name: string) =>
    `Operate: Decision Instance ${id} of ${name}`,
};

const PILL_TYPE = {
  TIMESTAMP: 'TIMESTAMP',
  FILTER: 'FILTER',
} as const;

const INCIDENTS_BAR_HEIGHT = 42;

const PAGE_TOP_PADDING = 48;

const MODIFICATION_HEADER_HEIGHT = 34;
const FOOTER_HEIGHT = 38;
const COLLAPSABLE_PANEL_MIN_WIDTH = 'var(--cds-spacing-09)';

export {
  ACTIVE_OPERATION_STATES,
  SORT_ORDER,
  FLOW_NODE_STATE_OVERLAY_ID,
  STATISTICS_OVERLAY_ID,
  PAGE_TITLE,
  PILL_TYPE,
  INCIDENTS_BAR_HEIGHT,
  NON_APPENDABLE_FLOW_NODES,
  MODIFICATION_HEADER_HEIGHT,
  FOOTER_HEIGHT,
  PAGE_TOP_PADDING,
  PERMISSIONS,
  COLLAPSABLE_PANEL_MIN_WIDTH,
};
