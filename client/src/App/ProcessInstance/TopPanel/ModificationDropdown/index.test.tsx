/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rest} from 'msw';
import {createRef} from 'react';
import {render, screen, waitFor} from 'modules/testing-library';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {ModificationDropdown} from './';
import {createInstance} from 'modules/testUtils';
import {mockProcessForModifications} from 'modules/mocks/mockProcessForModifications';
import {MemoryRouter} from 'react-router-dom';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {PROCESS_INSTANCE_ID} from 'modules/mocks/metadata';
import {modificationsStore} from 'modules/stores/modifications';
import {mockServer} from 'modules/mock-server/node';
import {flowNodeStatesStore} from 'modules/stores/flowNodeStates';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/processes/1']}>{children}</MemoryRouter>
    </ThemeProvider>
  );
};

const renderPopover = () => {
  const {container} = render(<svg />);
  const ref = createRef<HTMLDivElement>();

  return render(
    <ModificationDropdown
      selectedFlowNodeRef={container.querySelector('svg') ?? undefined}
      diagramCanvasRef={ref}
    />,
    {
      wrapper: Wrapper,
    }
  );
};

describe('Modification Dropdown', () => {
  beforeEach(() => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessForModifications))
      )
    );
    mockServer.use(
      rest.get(
        '/api/process-instances/:processId/flow-node-states',
        (_, res, ctx) =>
          res.once(
            ctx.json({
              StartEvent_1: 'COMPLETED',
              'service-task-1': 'COMPLETED',
              'multi-instance-subprocess': 'INCIDENT',
              'subprocess-start-1': 'COMPLETED',
              'subprocess-service-task': 'INCIDENT',
              'service-task-7': 'ACTIVE',
              'message-boundary': 'ACTIVE',
            })
          )
      )
    );

    flowNodeSelectionStore.init();
    flowNodeStatesStore.init('processId');
    processInstanceDetailsDiagramStore.init();
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
        processId: 'processId',
      })
    );
  });

  afterEach(() => {
    flowNodeSelectionStore.reset();
    processInstanceDetailsStore.reset();
    modificationsStore.reset();
    flowNodeStatesStore.reset();
  });

  it('should not render dropdown when no flow node is selected', async () => {
    renderPopover();

    await waitFor(() =>
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel
      ).not.toBeNull()
    );

    modificationsStore.enableModificationMode();

    expect(
      screen.queryByText(/Flow Node Modifications/)
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle(/Add single flow node instance/)
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle(
        /Cancel all running flow node instances in this flow node/
      )
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle(
        /Move all running instances in this flow node to another target/
      )
    ).not.toBeInTheDocument();

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-7',
    });

    expect(screen.getByText(/Flow Node Modifications/)).toBeInTheDocument();
    expect(
      screen.getByTitle(/Add single flow node instance/)
    ).toHaveTextContent(/Add/);
    expect(
      screen.getByTitle(
        /Cancel all running flow node instances in this flow node/
      )
    ).toHaveTextContent(/Cancel/);
    expect(
      screen.getByTitle(
        /Move all running instances in this flow node to another target/
      )
    ).toHaveTextContent(/Move/);
  });

  it('should not render dropdown when moving token', async () => {
    const {user} = renderPopover();

    await waitFor(() =>
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel
      ).not.toBeNull()
    );
    modificationsStore.enableModificationMode();

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-7',
    });

    expect(screen.getByText(/Flow Node Modifications/)).toBeInTheDocument();
    await user.click(await screen.findByText(/Move/));
    expect(
      screen.queryByText(/Flow Node Modifications/)
    ).not.toBeInTheDocument();
  });

  it('should only render add option for completed flow nodes', async () => {
    renderPopover();

    await waitFor(() =>
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel
      ).not.toBeNull()
    );
    modificationsStore.enableModificationMode();

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-1',
    });

    expect(screen.getByText(/Flow Node Modifications/)).toBeInTheDocument();
    expect(await screen.findByText(/Add/)).toBeInTheDocument();
    expect(screen.queryByText(/Cancel/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Move/)).not.toBeInTheDocument();
  });

  it('should only render move and cancel options for boundary events', async () => {
    renderPopover();

    await waitFor(() =>
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel
      ).not.toBeNull()
    );
    modificationsStore.enableModificationMode();

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'message-boundary',
    });

    expect(screen.getByText(/Flow Node Modifications/)).toBeInTheDocument();
    expect(await screen.findByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
  });

  it('should render unsupported flow node type for non modifiable flow nodes', async () => {
    renderPopover();

    await waitFor(() =>
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel
      ).not.toBeNull()
    );
    modificationsStore.enableModificationMode();

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'boundary-event',
    });

    expect(screen.getByText(/Flow Node Modifications/)).toBeInTheDocument();
    expect(
      await screen.findByText(/Unsupported flow node type/)
    ).toBeInTheDocument();
    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Cancel/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Move/)).not.toBeInTheDocument();
  });
});
