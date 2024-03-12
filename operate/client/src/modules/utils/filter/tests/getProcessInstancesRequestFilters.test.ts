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

import {getProcessInstancesRequestFilters} from '../index';

describe('getProcessInstancesRequestFilters', () => {
  const originalLocation = window.location;

  afterEach(() => {
    window.location = originalLocation;
  });

  it('should get process instances request filters', () => {
    const mockLocation = {
      search:
        '?active=true&incidents=true&completed=true&canceled=true&operationId=92449c1a-9d7a-4743-aaa3-f0661ead1bce&ids=9007199254741677&parentInstanceId=9007199254741678&startDateAfter=2023-09-05T00%3A00%3A00.000%2B0200&startDateBefore=2023-09-22T23%3A59%3A59.000%2B0200&endDateAfter=2023-09-01T00%3A00%3A00.000%2B0200&endDateBefore=2023-09-02T23%3A59%3A59.000%2B0200&errorMessage=test&process=bigVarProcess&version=1&flowNodeId=ServiceTask_0kt6c5i',
    };

    Object.defineProperty(window, 'location', {
      writable: true,
      value: mockLocation,
    });

    expect(getProcessInstancesRequestFilters()).toEqual({
      active: true,
      activityId: 'ServiceTask_0kt6c5i',
      batchOperationId: '92449c1a-9d7a-4743-aaa3-f0661ead1bce',
      canceled: true,
      completed: true,
      endDateAfter: '2023-09-01T00:00:00.000+0200',
      endDateBefore: '2023-09-02T23:59:59.000+0200',
      errorMessage: 'test',
      finished: true,
      ids: ['9007199254741677'],
      incidents: true,
      parentInstanceId: '9007199254741678',
      running: true,
      startDateAfter: '2023-09-05T00:00:00.000+0200',
      startDateBefore: '2023-09-22T23:59:59.000+0200',
    });
  });

  it('should not include tenant in request filters if value is all', () => {
    const mockLocation = {
      search: '?active=true&incidents=true&tenant=all',
    };

    Object.defineProperty(window, 'location', {
      writable: true,
      value: mockLocation,
    });

    expect(getProcessInstancesRequestFilters()).toEqual({
      active: true,
      incidents: true,
      running: true,
    });
  });

  it('should include tenant in request filters', () => {
    const mockLocation = {
      search: '?active=true&incidents=true&tenant=tenant-A',
    };

    Object.defineProperty(window, 'location', {
      writable: true,
      value: mockLocation,
    });

    expect(getProcessInstancesRequestFilters()).toEqual({
      active: true,
      incidents: true,
      running: true,
      tenantId: 'tenant-A',
    });
  });
});
