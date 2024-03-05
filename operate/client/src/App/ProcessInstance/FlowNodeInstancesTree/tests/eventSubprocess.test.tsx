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

import {render, screen, waitFor} from 'modules/testing-library';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {FlowNodeInstancesTree} from '../index';

import {
  eventSubProcessFlowNodeInstances,
  mockFlowNodeInstance,
  processId,
  processInstanceId,
  Wrapper,
  eventSubprocessProcessInstance,
} from './mocks';
import {eventSubProcess} from 'modules/testUtils';
import {createRef} from 'react';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockFetchFlowNodeInstances} from 'modules/mocks/api/fetchFlowNodeInstances';

describe('FlowNodeInstancesTree - Event Subprocess', () => {
  beforeEach(async () => {
    mockFetchProcessInstance().withSuccess(eventSubprocessProcessInstance);
    mockFetchProcessXML().withSuccess(eventSubProcess);

    await processInstanceDetailsDiagramStore.fetchProcessXml(processId);

    processInstanceDetailsStore.init({id: processInstanceId});
    flowNodeInstanceStore.init();
  });

  it('should be able to unfold and fold event subprocesses', async () => {
    mockFetchFlowNodeInstances().withSuccess(
      eventSubProcessFlowNodeInstances.level1,
    );

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    const {user} = render(
      <FlowNodeInstancesTree
        flowNodeInstance={{...mockFlowNodeInstance, state: 'ACTIVE'}}
        scrollableContainerRef={createRef<HTMLElement>()}
        isRoot
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      screen.queryByLabelText('Event Subprocess', {
        selector: "[aria-expanded='true']",
      }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('Interrupting timer')).not.toBeInTheDocument();

    mockFetchFlowNodeInstances().withSuccess(
      eventSubProcessFlowNodeInstances.level2,
    );

    await user.type(
      screen.getByLabelText('Event Subprocess', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );

    expect(
      await screen.findByLabelText('Event Subprocess', {
        selector: "[aria-expanded='true']",
      }),
    ).toBeInTheDocument();

    expect(await screen.findByText('Interrupting timer')).toBeInTheDocument();

    await user.type(
      screen.getByLabelText('Event Subprocess', {
        selector: "[aria-expanded='true']",
      }),
      '{arrowleft}',
    );

    expect(screen.queryByText('Interrupting timer')).not.toBeInTheDocument();
  });
});
