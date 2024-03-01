/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
