/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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

type NonEmptyArray<T> = [T, ...T[]];

type Permissions = NonEmptyArray<'read' | 'write'>;

type CurrentUser = {
  userId: string;
  displayName: string | null;
  permissions: Permissions;
  roles: string[] | null;
  salesPlanType: string | null;
  c8Links: {
    name: 'console' | 'modeler' | 'tasklist' | 'operate' | 'optimize';
    link: string;
  }[];
  tenants: {
    id: string;
    name: string;
  }[];
  groups: string[];
};

type FullVariable = {
  id: string;
  name: string;
  value: string;
  previewValue: string;
  isValueTruncated: false;
};

type TruncatedVariable = {
  id: string;
  name: string;
  value: null;
  previewValue: string;
  isValueTruncated: true;
};

type Variable = FullVariable | TruncatedVariable;

type TaskState = 'CREATED' | 'COMPLETED' | 'CANCELED';

type Task = {
  id: string;
  name: string;
  taskDefinitionId: string;
  processName: string;
  creationDate: string;
  followUpDate: string | null;
  dueDate: string | null;
  completionDate: string | null;
  assignee: string | null;
  taskState: TaskState;
  sortValues: [string, string];
  isFirst: boolean;
  formKey: string | null;
  formVersion: number | null | undefined;
  formId: string | null;
  isFormEmbedded: boolean | null;
  processInstanceKey: string;
  processDefinitionKey: string;
  candidateGroups: string[];
  candidateUsers: string[];
  tenantId: string | '<default>';
  context: string | null;
};

type Form = {
  id: string;
  processDefinitionKey: string;
  schema: string;
  title: string;
  version: number | null;
  tenantId: string;
  isDeleted: boolean;
};

type Process = {
  id: string;
  name: string | null;
  bpmnProcessId: string;
  version: number;
  startEventFormId: string | null;
  sortValues: [string];
};

type ProcessInstance = {
  id: string;
  process: Process;
  state: 'active' | 'completed' | 'canceled' | 'incident' | 'terminated';
  creationDate: string;
  sortValues: [string, string];
  isFirst: boolean;
};

type DateSearch = {
  from?: string;
  to?: string;
};

type PaginationSearchPair = [string, string];

type TasksSearchBody = {
  state?: TaskState;
  followUpDate?: DateSearch;
  dueDate?: DateSearch;
  assigned?: boolean;
  assignee?: string;
  taskDefinitionId?: string;
  candidateGroup?: string;
  candidateUser?: string;
  processDefinitionKey?: string;
  processInstanceKey?: string;
  pageSize?: number;
  sort?: Array<{
    field: 'completionTime' | 'creationTime' | 'followUpDate' | 'dueDate';
    order: 'ASC' | 'DESC';
  }>;
  searchAfter?: PaginationSearchPair;
  searchAfterOrEqual?: PaginationSearchPair;
  searchBefore?: PaginationSearchPair;
  searchBeforeOrEqual?: PaginationSearchPair;
  tenantIds?: string[];
  taskVariables?: Array<{
    name: string;
    value: string;
    operator: 'eq';
  }>;
};

export type {
  CurrentUser,
  Variable,
  Task,
  TaskState,
  Form,
  Permissions,
  Process,
  ProcessInstance,
  TasksSearchBody,
  FullVariable,
  TruncatedVariable,
};
