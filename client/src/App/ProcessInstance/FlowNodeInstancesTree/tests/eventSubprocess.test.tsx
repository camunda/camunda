/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {FlowNodeInstancesTree} from '../index';

import {
  multiInstanceProcessInstance,
  eventSubProcessFlowNodeInstances,
  mockFlowNodeInstance,
  processId,
  processInstanceId,
  Wrapper,
} from './mocks';
import {eventSubProcess} from 'modules/testUtils';
import {createRef} from 'react';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockFetchFlowNodeInstances} from 'modules/mocks/api/fetchFlowNodeInstances';

describe('FlowNodeInstancesTree - Event Subprocess', () => {
  beforeEach(async () => {
    mockFetchProcessInstance().withSuccess(multiInstanceProcessInstance);
    mockFetchProcessXML().withSuccess(eventSubProcess);

    await processInstanceDetailsDiagramStore.fetchProcessXml(processId);

    processInstanceDetailsStore.init({id: processInstanceId});
    flowNodeInstanceStore.init();
  });

  it('should be able to unfold and fold event subprocesses', async () => {
    mockFetchFlowNodeInstances().withSuccess(
      eventSubProcessFlowNodeInstances.level1
    );

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    const {user} = render(
      <FlowNodeInstancesTree
        treeDepth={1}
        flowNodeInstance={mockFlowNodeInstance}
        isLastChild={false}
        scrollableContainerRef={createRef<HTMLElement>()}
      />,
      {
        wrapper: Wrapper,
      }
    );

    expect(
      screen.queryByLabelText('Fold Event Subprocess')
    ).not.toBeInTheDocument();
    expect(screen.queryByText('Interrupting timer')).not.toBeInTheDocument();

    mockFetchFlowNodeInstances().withSuccess(
      eventSubProcessFlowNodeInstances.level2
    );

    await user.click(screen.getByLabelText('Unfold Event Subprocess'));

    expect(
      await screen.findByLabelText('Fold Event Subprocess')
    ).toBeInTheDocument();

    expect(screen.getByText('Interrupting timer')).toBeInTheDocument();

    await user.click(screen.getByLabelText('Fold Event Subprocess'));

    expect(screen.queryByText('Interrupting timer')).not.toBeInTheDocument();
  });
});
