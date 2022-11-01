/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {rest} from 'msw';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {mockServer} from 'modules/mock-server/node';
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
} from './mocks';
import {eventSubProcess} from 'modules/testUtils';
import {createRef} from 'react';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';

describe('FlowNodeInstancesTree - Event Subprocess', () => {
  beforeEach(async () => {
    mockFetchProcessInstance().withSuccess(multiInstanceProcessInstance);

    mockServer.use(
      rest.get(`/api/processes/:processId/xml`, (_, res, ctx) =>
        res.once(ctx.text(eventSubProcess))
      )
    );
    await processInstanceDetailsDiagramStore.fetchProcessXml(processId);

    processInstanceDetailsStore.init({id: processInstanceId});
    flowNodeInstanceStore.init();
  });

  afterEach(() => {
    processInstanceDetailsStore.reset();
    processInstanceDetailsDiagramStore.reset();
    flowNodeInstanceStore.reset();
  });

  it('should be able to unfold and fold event subprocesses', async () => {
    mockServer.use(
      rest.post(`/api/flow-node-instances`, (_, res, ctx) =>
        res.once(ctx.json(eventSubProcessFlowNodeInstances.level1))
      ),
      rest.post(`/api/flow-node-instances`, (_, res, ctx) =>
        res.once(ctx.json(eventSubProcessFlowNodeInstances.level2))
      )
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
        wrapper: ThemeProvider,
      }
    );

    expect(
      screen.queryByLabelText('Fold Event Subprocess')
    ).not.toBeInTheDocument();
    expect(screen.queryByText('Interrupting timer')).not.toBeInTheDocument();

    await user.click(screen.getByLabelText('Unfold Event Subprocess'));

    expect(
      await screen.findByLabelText('Fold Event Subprocess')
    ).toBeInTheDocument();

    expect(screen.getByText('Interrupting timer')).toBeInTheDocument();

    await user.click(screen.getByLabelText('Fold Event Subprocess'));

    expect(screen.queryByText('Interrupting timer')).not.toBeInTheDocument();
  });
});
