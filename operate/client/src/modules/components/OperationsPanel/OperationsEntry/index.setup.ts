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

type OperationsMock = {
  RETRY: OperationEntity;
  CANCEL: OperationEntity;
  EDIT: OperationEntity;
  DELETE_PROCESS_INSTANCE: OperationEntity;
  DELETE_PROCESS_DEFINITION: OperationEntity;
  DELETE_DECISION_DEFINITION: OperationEntity;
  MODIFY: OperationEntity;
  MIGRATE: OperationEntity;
  MOVE: OperationEntity;
};

const OPERATIONS: OperationsMock = {
  RETRY: {
    id: 'b42fd629-73b1-4709-befb-7ccd900fb18d',
    type: 'RESOLVE_INCIDENT',
    endDate: null,
    operationsTotalCount: 2,
    operationsFinishedCount: 1,
    instancesCount: 1,
    name: null,
    startDate: '2021-02-20T18:31:18.625+0100',
    sortValues: ['1613842299289', '1613842278625'],
  },
  CANCEL: {
    id: '393ad666-d7f0-45c9-a679-ffa0ef82f88a',
    type: 'CANCEL_PROCESS_INSTANCE',
    endDate: '2020-02-06T14:56:17.932+0100',
    operationsTotalCount: 2,
    operationsFinishedCount: 2,
    instancesCount: 1,
    name: null,
    startDate: '2021-02-20T18:31:18.625+0100',
    sortValues: ['1613842299289', '1613842278625'],
  },
  EDIT: {
    id: 'df325d44-6a4c-4428-b017-24f923f1d052',
    type: 'UPDATE_VARIABLE',
    endDate: '2020-02-06T14:56:17.932+0100',
    operationsTotalCount: 4,
    operationsFinishedCount: 4,
    instancesCount: 1,
    name: null,
    startDate: '2021-02-20T18:31:18.625+0100',
    sortValues: ['1613842299289', '1613842278625'],
  },
  DELETE_PROCESS_INSTANCE: {
    id: 'df325d44-6a4c-4428-b017-24f923f1d052',
    type: 'DELETE_PROCESS_INSTANCE',
    endDate: '2020-02-06T14:56:17.932+0100',
    operationsTotalCount: 4,
    operationsFinishedCount: 4,
    instancesCount: 1,
    name: null,
    startDate: '2021-02-20T18:31:18.625+0100',
    sortValues: ['1613842299289', '1613842278625'],
  },
  MODIFY: {
    id: 'df325d44-6a4c-4428-b017-24f923f1d052',
    type: 'MODIFY_PROCESS_INSTANCE',
    endDate: '2020-02-06T14:56:17.932+0100',
    operationsTotalCount: 4,
    operationsFinishedCount: 4,
    instancesCount: 1,
    name: null,
    startDate: '2021-02-20T18:31:18.625+0100',
    sortValues: ['1613842299289', '1613842278625'],
  },
  DELETE_PROCESS_DEFINITION: {
    id: '5de66f22-a438-40f8-a89c-904g2dgfjm28',
    name: 'ProcessDefinitionA - version 1',
    type: 'DELETE_PROCESS_DEFINITION',
    startDate: '2020-02-06T14:56:17.932+0100',
    endDate: '2023-02-16T14:23:45.306+0100',
    instancesCount: 1,
    operationsTotalCount: 1,
    operationsFinishedCount: 1,
  },
  DELETE_DECISION_DEFINITION: {
    id: '5de66f22-a438-40f8-a89c-fn298fn23988',
    name: 'DecisionDefinitionA - version 1',
    type: 'DELETE_DECISION_DEFINITION',
    startDate: '2023-02-16T14:23:45.306+0100',
    endDate: null,
    instancesCount: 23,
    operationsTotalCount: 23,
    operationsFinishedCount: 10,
  },
  MIGRATE: {
    id: '8ba1a9a7-8537-4af3-97dc-f7249743b20b',
    name: null,
    type: 'MIGRATE_PROCESS_INSTANCE',
    startDate: '2023-11-22T09:02:30.178+0100',
    endDate: '2023-11-22T09:03:29.564+0100',
    instancesCount: 1,
    operationsTotalCount: 1,
    operationsFinishedCount: 1,
  },
  MOVE: {
    id: '8ba1a9a7-8537-4af3-97dc-f7249743b20b',
    name: null,
    type: 'MOVE_TOKEN',
    startDate: '2023-11-22T09:02:30.178+0100',
    endDate: '2023-11-22T09:03:29.564+0100',
    instancesCount: 1,
    operationsTotalCount: 1,
    operationsFinishedCount: 1,
  },
};

const mockProps = {
  onInstancesClick: jest.fn(),
};

export {OPERATIONS, mockProps};
