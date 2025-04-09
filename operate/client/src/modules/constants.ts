/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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

const TOKEN_OPERATIONS = {
  ADD_TOKEN: 'ADD_TOKEN',
  MOVE_TOKEN: 'MOVE_TOKEN',
  CANCEL_TOKEN: 'CANCEL_TOKEN',
} as const;

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
  TOKEN_OPERATIONS as MODIFICATION_OPERATIONS,
  SORT_ORDER,
  PAGE_TITLE,
  PAGE_TOP_PADDING,
  PERMISSIONS,
  COLLAPSABLE_PANEL_MIN_WIDTH,
  INSTANCE_HISTORY_LEFT_PADDING,
  COLLAPSABLE_PANEL_HEADER_HEIGHT,
  ARROW_ICON_WIDTH,
  DEFAULT_TENANT,
};
