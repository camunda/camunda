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

import {Task} from 'modules/types';
import {currentUser} from './current-user';
import {DEFAULT_TENANT_ID} from 'modules/constants/multiTenancy';
import {formatRFC3339} from 'date-fns';

const tasks: Task[] = [
  {
    id: '0',
    name: 'name',
    processName: 'processName',
    creationDate: '2024-05-28 10:11:12',
    completionDate: formatRFC3339(new Date()),
    assignee: currentUser.userId,
    taskState: 'CREATED',
    sortValues: ['0', '1'],
    isFirst: true,
    formKey: null,
    formId: null,
    formVersion: null,
    isFormEmbedded: null,
    processDefinitionKey: 'process-definition-id',
    taskDefinitionId: 'task-definition-id',
    processInstanceKey: 'process-instance-key',
    followUpDate: null,
    dueDate: null,
    candidateGroups: [],
    candidateUsers: [],
    tenantId: DEFAULT_TENANT_ID,
    context: 'My Task',
  },
  {
    id: '1',
    name: 'name',
    processName: 'processName',
    creationDate: '2024-05-29 13:14:15',
    completionDate: formatRFC3339(new Date()),
    assignee: 'mustermann',
    taskState: 'CREATED',
    sortValues: ['1', '2'],
    isFirst: false,
    formKey: null,
    formId: null,
    formVersion: null,
    isFormEmbedded: null,
    processDefinitionKey: 'process-definition-id',
    taskDefinitionId: 'task-definition-id',
    processInstanceKey: 'process-instance-key',
    followUpDate: null,
    dueDate: null,
    candidateGroups: [],
    candidateUsers: [],
    tenantId: DEFAULT_TENANT_ID,
    context: 'My Task',
  },
  {
    id: '2',
    name: 'name',
    processName: 'processName',
    creationDate: '2024-05-30 16:17:18',
    completionDate: formatRFC3339(new Date()),
    assignee: null,
    taskState: 'CREATED',
    sortValues: ['2', '3'],
    isFirst: false,
    formKey: null,
    formId: null,
    formVersion: null,
    isFormEmbedded: null,
    processDefinitionKey: 'process-definition-id',
    taskDefinitionId: 'task-definition-id',
    processInstanceKey: 'process-instance-key',
    followUpDate: null,
    dueDate: null,
    candidateGroups: [],
    candidateUsers: [],
    tenantId: DEFAULT_TENANT_ID,
    context: 'My Task',
  },
];

const tasksAssignedToDemoUser: Task[] = tasks.map((task) => ({
  ...task,
  assignee: currentUser.userId,
}));

const unassignedTasks: Task[] = tasks.map((task) => ({
  ...task,
  assignee: null,
}));

const completedTasks: Task[] = tasks.map((task) => ({
  ...task,
  assignee: task.assignee === null ? currentUser.userId : task.assignee,
  taskState: 'COMPLETED',
}));

const generateTask = (id: string, name?: string): Task => {
  return {
    id,
    name: name ?? `TASK ${id}`,
    processName: 'Flight registration',
    assignee: 'demo',
    creationDate: '2024-01-13T12:13:18.655Z',
    taskState: 'CREATED',
    sortValues: [id, id],
    followUpDate: null,
    dueDate: null,
    isFirst: false,
    processDefinitionKey: 'process-definition-id',
    taskDefinitionId: 'task-definition-id',
    processInstanceKey: 'process-instance-key',
    completionDate: null,
    formKey: null,
    formId: null,
    formVersion: null,
    isFormEmbedded: null,
    candidateGroups: [],
    candidateUsers: [],
    tenantId: DEFAULT_TENANT_ID,
    context: 'My Task',
  };
};

export {
  tasks,
  tasksAssignedToDemoUser,
  unassignedTasks,
  completedTasks,
  generateTask,
};
