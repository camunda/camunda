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
import {modificationsStore} from 'modules/stores/modifications';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {multiInstanceProcess} from 'modules/testUtils';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {generateUniqueID} from 'modules/utils/generateUniqueID';
import {FlowNodeInstancesTree} from '..';
import {
  CURRENT_INSTANCE,
  flowNodeInstances,
  mockFlowNodeInstance,
  multipleFlowNodeInstances,
  processId,
  processInstanceId,
} from './mocks';

describe('FlowNodeInstancesTree - Modification placeholders', () => {
  beforeEach(async () => {
    mockServer.use(
      rest.get(`/api/process-instances/:processInstanceId`, (_, res, ctx) =>
        res.once(ctx.json(CURRENT_INSTANCE))
      ),
      rest.get(`/api/processes/:processId/xml`, (_, res, ctx) =>
        res.once(ctx.text(multiInstanceProcess))
      )
    );

    await processInstanceDetailsDiagramStore.fetchProcessXml(processId);
  });

  afterEach(() => {
    processInstanceDetailsStore.reset();
    processInstanceDetailsDiagramStore.reset();
    flowNodeInstanceStore.reset();
    modificationsStore.reset();
  });

  it('should show and remove two add modification flow nodes', async () => {
    processInstanceDetailsStore.init({id: processInstanceId});
    flowNodeInstanceStore.init();

    mockServer.use(
      rest.post(`/api/flow-node-instances`, (_, res, ctx) =>
        res.once(ctx.json(flowNodeInstances.level1))
      )
    );

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    render(
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

    expect(screen.queryByText('Peter Join')).not.toBeInTheDocument();

    // modification icons
    expect(screen.queryByText('plus.svg')).not.toBeInTheDocument();
    expect(
      screen.queryByText('warning-message-icon.svg')
    ).not.toBeInTheDocument();
    expect(screen.queryByText('stop.svg')).not.toBeInTheDocument();

    modificationsStore.enableModificationMode();
    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        scopeId: generateUniqueID(),
        flowNode: {id: 'peterJoin', name: 'Peter Join'},
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        parentScopeIds: {},
      },
    });
    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        scopeId: generateUniqueID(),
        flowNode: {id: 'peterJoin', name: 'Peter Join'},
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        parentScopeIds: {},
      },
    });
    expect(screen.getByText('Multi-Instance Process')).toBeInTheDocument();
    expect(screen.getByText('Peter Fork')).toBeInTheDocument();
    expect(screen.getAllByText('Peter Join')).toHaveLength(2);

    // modification icons
    expect(screen.getAllByText('plus.svg')).toHaveLength(2);
    expect(screen.getAllByText('warning-message-icon.svg')).toHaveLength(2);
    expect(screen.queryByText('stop.svg')).not.toBeInTheDocument();

    expect(
      screen.getByText('Filter-Map Sub Process (Multi Instance)')
    ).toBeInTheDocument();

    modificationsStore.reset();

    expect(screen.queryByText('Peter Join')).not.toBeInTheDocument();
    // modification icons
    expect(screen.queryByText('plus.svg')).not.toBeInTheDocument();
    expect(
      screen.queryByText('warning-message-icon.svg')
    ).not.toBeInTheDocument();
    expect(screen.queryByText('stop.svg')).not.toBeInTheDocument();
  });

  it('should show and remove one cancel modification flow nodes', async () => {
    processInstanceDetailsStore.init({id: processInstanceId});
    flowNodeInstanceStore.init();

    mockServer.use(
      rest.post(`/api/flow-node-instances`, (_, res, ctx) =>
        res.once(ctx.json(multipleFlowNodeInstances))
      )
    );

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    render(
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

    // modification icons
    expect(screen.queryByText('plus.svg')).not.toBeInTheDocument();
    expect(
      screen.queryByText('warning-message-icon.svg')
    ).not.toBeInTheDocument();
    expect(screen.queryByText('stop.svg')).not.toBeInTheDocument();

    modificationsStore.enableModificationMode();
    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'CANCEL_TOKEN',
        flowNode: {id: 'peterJoin', name: 'Peter Join'},
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
      },
    });

    expect(screen.getByText('Multi-Instance Process')).toBeInTheDocument();
    expect(screen.getAllByText('Peter Join')).toHaveLength(2);

    // modification icons
    expect(screen.getByText('stop.svg')).toBeInTheDocument();
    expect(screen.queryByText('plus.svg')).not.toBeInTheDocument();
    expect(
      screen.queryByText('warning-message-icon.svg')
    ).not.toBeInTheDocument();

    modificationsStore.reset();

    // modification icons
    expect(screen.queryByText('stop.svg')).not.toBeInTheDocument();
    expect(screen.queryByText('plus.svg')).not.toBeInTheDocument();
    expect(
      screen.queryByText('warning-message-icon.svg')
    ).not.toBeInTheDocument();
  });
});
