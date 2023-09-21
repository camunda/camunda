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

const PAGE_TOP_PADDING = 48;
const COLLAPSABLE_PANEL_MIN_WIDTH = 'var(--cds-spacing-09)';
const INSTANCE_HISTORY_LEFT_PADDING = 'var(--cds-spacing-05)';
const COLLAPSABLE_PANEL_HEADER_HEIGHT = 'var(--cds-spacing-09)';
const ARROW_ICON_WIDTH = 'var(--cds-spacing-08)';
const DEFAULT_TENANT = '<default>';

export {
  ACTIVE_OPERATION_STATES,
  SORT_ORDER,
  PAGE_TITLE,
  NON_APPENDABLE_FLOW_NODES,
  PAGE_TOP_PADDING,
  PERMISSIONS,
  COLLAPSABLE_PANEL_MIN_WIDTH,
  INSTANCE_HISTORY_LEFT_PADDING,
  COLLAPSABLE_PANEL_HEADER_HEIGHT,
  ARROW_ICON_WIDTH,
  DEFAULT_TENANT,
};
