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

import {sortOperations} from './sortOperations';

const MOCK_RUNNING_OPERATION = Object.freeze({
  id: '8a2e3d79-b5ec-4cef-92cd-6ead2035b972',
  name: null,
  type: 'RESOLVE_INCIDENT',
  startDate: '2020-03-23T15:29:19.170+0100',
  endDate: null,
  instancesCount: 893,
  operationsTotalCount: 406,
  operationsFinishedCount: 0,
  sortValues: ['9223372036854775807', '1584973759170'],
});
const MOCK_FINISHED_OPERATIONS_RESOLVE_INCIDENT = Object.freeze({
  id: '21ac5d59-cdf6-48cd-b467-00c0c8ffeeb3',
  name: null,
  type: 'RESOLVE_INCIDENT',
  startDate: '2020-03-23T11:46:37.960+0100',
  endDate: '2020-03-23T11:46:55.713+0100',
  instancesCount: 893,
  operationsTotalCount: 406,
  operationsFinishedCount: 406,
  sortValues: ['1584960415713', '1584960397960'],
});
const MOCK_FINISHED_OPERATIONS_CANCEL_PROCESS_INSTANCE = Object.freeze({
  id: 'b22d5134-64de-4dbb-af9b-a211aaebed47',
  name: null,
  type: 'CANCEL_PROCESS_INSTANCE',
  startDate: '2020-03-20T19:31:09.478+0100',
  endDate: '2020-03-20T19:31:17.637+0100',
  instancesCount: 1,
  operationsTotalCount: 1,
  operationsFinishedCount: 1,
  sortValues: ['1584729077637', '1584729069478'],
});

describe('sortOperations', () => {
  it('should put running operations first', () => {
    expect(
      sortOperations([
        MOCK_FINISHED_OPERATIONS_RESOLVE_INCIDENT,
        MOCK_FINISHED_OPERATIONS_CANCEL_PROCESS_INSTANCE,
        MOCK_RUNNING_OPERATION,
      ]),
    ).toEqual([
      MOCK_RUNNING_OPERATION,
      MOCK_FINISHED_OPERATIONS_RESOLVE_INCIDENT,
      MOCK_FINISHED_OPERATIONS_CANCEL_PROCESS_INSTANCE,
    ]);
  });

  it('should order finished operations by end date', () => {
    expect(
      sortOperations([
        MOCK_FINISHED_OPERATIONS_CANCEL_PROCESS_INSTANCE,
        MOCK_FINISHED_OPERATIONS_RESOLVE_INCIDENT,
      ]),
    ).toEqual([
      MOCK_FINISHED_OPERATIONS_RESOLVE_INCIDENT,
      MOCK_FINISHED_OPERATIONS_CANCEL_PROCESS_INSTANCE,
    ]);
  });
});
