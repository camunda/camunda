/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rest} from 'msw';
import {createRef} from 'react';
import {render, screen, waitFor} from 'modules/testing-library';
import {mockServer} from 'modules/mock-server/node';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {open} from 'modules/mocks/diagrams';
import {
  nestedSubProcessesInstance,
  nestedSubProcessFlowNodeInstances,
  nestedSubProcessFlowNodeInstance,
} from './mocks';
import {FlowNodeInstancesTree} from '..';
import {modificationsStore} from 'modules/stores/modifications';
import {generateUniqueID} from 'modules/utils/generateUniqueID';
import {instanceHistoryModificationStore} from 'modules/stores/instanceHistoryModification';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';

describe('FlowNodeInstancesTree - Nested Subprocesses', () => {
  beforeEach(async () => {
    mockFetchProcessInstance().withSuccess(nestedSubProcessesInstance);
    mockFetchProcessXML().withSuccess(open('NestedSubProcesses.bpmn'));

    await processInstanceDetailsDiagramStore.fetchProcessXml(
      nestedSubProcessesInstance.bpmnProcessId
    );

    processInstanceDetailsStore.init({id: nestedSubProcessesInstance.id});
    flowNodeInstanceStore.init();

    mockServer.use(
      rest.post(`/api/flow-node-instances`, (_, res, ctx) =>
        res.once(ctx.json(nestedSubProcessFlowNodeInstances))
      )
    );

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });
  });

  afterEach(() => {
    processInstanceDetailsStore.reset();
    processInstanceDetailsDiagramStore.reset();
    flowNodeInstanceStore.reset();
    modificationsStore.reset();
    instanceHistoryModificationStore.reset();
  });

  it('should add parent placeholders (ADD_TOKEN)', async () => {
    const {user} = render(
      <FlowNodeInstancesTree
        treeDepth={1}
        flowNodeInstance={nestedSubProcessFlowNodeInstance}
        isLastChild={false}
        scrollableContainerRef={createRef<HTMLElement>()}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(screen.getByText('Nested Sub Processes')).toBeInTheDocument();
    expect(screen.getByText('Start Event 1')).toBeInTheDocument();
    expect(screen.queryByText('Sub Process 1')).not.toBeInTheDocument();
    expect(screen.queryByText('Sub Process 2')).not.toBeInTheDocument();
    expect(screen.queryByText('User Task')).not.toBeInTheDocument();

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
    expect(screen.getByText('Sub Process 1')).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {name: 'Unfold Sub Process 1'})
    );
    expect(screen.getByText('Sub Process 2')).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {name: 'Unfold Sub Process 2'})
    );
    expect(screen.getByText('User Task')).toBeInTheDocument();

    modificationsStore.disableModificationMode();

    expect(screen.queryByText('Sub Process 1')).not.toBeInTheDocument();
    expect(screen.queryByText('Sub Process 2')).not.toBeInTheDocument();
    expect(screen.queryByText('User Task')).not.toBeInTheDocument();
  });

  it('should add parent placeholders (MOVE_TOKEN)', async () => {
    const {user} = render(
      <FlowNodeInstancesTree
        treeDepth={1}
        flowNodeInstance={nestedSubProcessFlowNodeInstance}
        isLastChild={false}
        scrollableContainerRef={createRef<HTMLElement>()}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(screen.getByText('Nested Sub Processes')).toBeInTheDocument();
    expect(screen.getByText('Start Event 1')).toBeInTheDocument();
    expect(screen.queryByText('Sub Process 1')).not.toBeInTheDocument();
    expect(screen.queryByText('Sub Process 2')).not.toBeInTheDocument();
    expect(screen.queryByText('User Task')).not.toBeInTheDocument();

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
    expect(screen.getByText('Sub Process 1')).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {name: 'Unfold Sub Process 1'})
    );
    expect(screen.getByText('Sub Process 2')).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {name: 'Unfold Sub Process 2'})
    );
    expect(screen.getAllByText('User Task')).toHaveLength(2);

    modificationsStore.disableModificationMode();

    expect(screen.queryByText('Sub Process 1')).not.toBeInTheDocument();
    expect(screen.queryByText('Sub Process 2')).not.toBeInTheDocument();
    expect(screen.queryByText('User Task')).not.toBeInTheDocument();
  });
});
