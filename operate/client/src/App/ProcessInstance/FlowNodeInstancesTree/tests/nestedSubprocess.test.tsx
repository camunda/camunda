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

import {createRef} from 'react';
import {render, screen, waitFor} from 'modules/testing-library';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {open} from 'modules/mocks/diagrams';
import {
  nestedSubProcessesInstance,
  nestedSubProcessFlowNodeInstances,
  nestedSubProcessFlowNodeInstance,
  Wrapper,
} from './mocks';
import {FlowNodeInstancesTree} from '..';
import {modificationsStore} from 'modules/stores/modifications';
import {generateUniqueID} from 'modules/utils/generateUniqueID';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockFetchFlowNodeInstances} from 'modules/mocks/api/fetchFlowNodeInstances';
import {act} from 'react-dom/test-utils';

describe('FlowNodeInstancesTree - Nested Subprocesses', () => {
  beforeEach(async () => {
    mockFetchProcessInstance().withSuccess(nestedSubProcessesInstance);
    mockFetchProcessXML().withSuccess(open('NestedSubProcesses.bpmn'));

    await processInstanceDetailsDiagramStore.fetchProcessXml(
      nestedSubProcessesInstance.bpmnProcessId,
    );

    processInstanceDetailsStore.init({id: nestedSubProcessesInstance.id});
    flowNodeInstanceStore.init();

    mockFetchFlowNodeInstances().withSuccess(nestedSubProcessFlowNodeInstances);

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });
  });

  it('should add parent placeholders (ADD_TOKEN)', async () => {
    const {user} = render(
      <FlowNodeInstancesTree
        flowNodeInstance={nestedSubProcessFlowNodeInstance}
        scrollableContainerRef={createRef<HTMLElement>()}
        isRoot
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(screen.getByText('Nested Sub Processes')).toBeInTheDocument();
    expect(screen.getByText('Start Event 1')).toBeInTheDocument();
    expect(screen.queryByText('Sub Process 1')).not.toBeInTheDocument();
    expect(screen.queryByText('Sub Process 2')).not.toBeInTheDocument();
    expect(screen.queryByText('User Task')).not.toBeInTheDocument();

    act(() => {
      modificationsStore.enableModificationMode();
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          scopeId: generateUniqueID(),
          flowNode: {id: 'UserTask', name: 'User Task'},
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          parentScopeIds: {
            SubProcess_1: generateUniqueID(),
            SubProcess_2: generateUniqueID(),
          },
        },
      });
    });

    expect(await screen.findByText('Sub Process 1')).toBeInTheDocument();

    await user.type(
      screen.getByLabelText('Sub Process 1', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );
    expect(await screen.findByText('Sub Process 2')).toBeInTheDocument();

    await user.type(
      screen.getByLabelText('Sub Process 2', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );
    expect(screen.getByText('User Task')).toBeInTheDocument();

    act(() => {
      modificationsStore.disableModificationMode();
    });

    expect(screen.queryByText('Sub Process 1')).not.toBeInTheDocument();
    expect(screen.queryByText('Sub Process 2')).not.toBeInTheDocument();
    expect(screen.queryByText('User Task')).not.toBeInTheDocument();
  });

  it('should add parent placeholders (MOVE_TOKEN)', async () => {
    const {user} = render(
      <FlowNodeInstancesTree
        flowNodeInstance={nestedSubProcessFlowNodeInstance}
        scrollableContainerRef={createRef<HTMLElement>()}
        isRoot
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(screen.getByText('Nested Sub Processes')).toBeInTheDocument();
    expect(screen.getByText('Start Event 1')).toBeInTheDocument();
    expect(screen.queryByText('Sub Process 1')).not.toBeInTheDocument();
    expect(screen.queryByText('Sub Process 2')).not.toBeInTheDocument();
    expect(screen.queryByText('User Task')).not.toBeInTheDocument();

    act(() => {
      modificationsStore.enableModificationMode();
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'MOVE_TOKEN',
          scopeIds: [generateUniqueID(), generateUniqueID()],
          flowNode: {id: 'StartEvent_1', name: 'Start Event 1'},
          targetFlowNode: {id: 'UserTask', name: 'User Task'},
          affectedTokenCount: 2,
          visibleAffectedTokenCount: 2,
          parentScopeIds: {
            SubProcess_1: generateUniqueID(),
            SubProcess_2: generateUniqueID(),
          },
        },
      });
    });

    expect(await screen.findByText('Sub Process 1')).toBeInTheDocument();

    await user.type(
      screen.getByLabelText('Sub Process 1', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );
    expect(await screen.findByText('Sub Process 2')).toBeInTheDocument();

    await user.type(
      screen.getByLabelText('Sub Process 2', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );
    expect(screen.getAllByText('User Task')).toHaveLength(2);

    act(() => {
      modificationsStore.disableModificationMode();
    });

    expect(screen.queryByText('Sub Process 1')).not.toBeInTheDocument();
    expect(screen.queryByText('Sub Process 2')).not.toBeInTheDocument();
    expect(screen.queryByText('User Task')).not.toBeInTheDocument();
  });
});
