/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {rest} from 'msw';
import {render, screen} from '@testing-library/react';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {mockServer} from 'modules/mock-server/node';
import {PopoverOverlay} from './';
import {
  createInstance,
  mockCallActivityProcessXML,
  mockProcessXML,
} from 'modules/testUtils';
import {mockIncidents} from 'modules/mocks/incidents';
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';
import userEvent from '@testing-library/user-event';
import {MemoryRouter} from 'react-router';
import {incidentsStore} from 'modules/stores/incidents';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';

const FLOW_NODE_ID = 'StartEvent_1'; // this need to match the id from mockProcessXML
const CALL_ACTIVITY_FLOW_NODE_ID = 'Activity_0zqism7'; // this need to match the id from mockCallActivityProcessXML
const FLOW_NODE_INSTANCE_ID = '2251799813699889';

const Wrapper: React.FC = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter>{children}</MemoryRouter>
    </ThemeProvider>
  );
};

const completedFlowNodeMetaData = {
  flowNodeInstanceId: FLOW_NODE_INSTANCE_ID,
  flowNodeId: null,
  flowNodeType: null,
  instanceCount: null,
  breadcrumb: [],
  instanceMetadata: {
    flowNodeId: CALL_ACTIVITY_FLOW_NODE_ID,
    flowNodeInstanceId: FLOW_NODE_INSTANCE_ID,
    flowNodeType: 'TASK_CALL_ACTIVITY',
    startDate: '2021-03-26T09:50:22.457+0000',
    endDate: '2021-03-26T11:00:00.000+0000',
    incidentErrorType: null,
    incidentErrorMessage: null,
    jobId: null,
    jobType: null,
    jobRetries: null,
    jobWorker: null,
    jobDeadline: '2021-03-26T10:00:00.000+0000',
    jobCustomHeaders: null,
    calledProcessInstanceId: '229843728748927482',
    calledProcessDefinitionName: 'Called Process',
  },
};

const incidentFlowNodeMetaData = {
  flowNodeInstanceId: FLOW_NODE_INSTANCE_ID,
  flowNodeId: null,
  flowNodeType: null,
  instanceCount: null,
  breadcrumb: [],
  instanceMetadata: {
    flowNodeId: FLOW_NODE_ID,
    flowNodeInstanceId: FLOW_NODE_INSTANCE_ID,
    flowNodeType: 'START_EVENT',
    startDate: '2021-03-26T10:00:00.000+0000',
    endDate: null,
    incidentErrorType: 'JOB_NO_RETRIES',
    incidentErrorMessage: 'No more retries left.',
    jobId: '2251799813690876',
    jobType: null,
    jobRetries: null,
    jobWorker: null,
    jobDeadline: null,
    jobCustomHeaders: null,
    calledProcessInstanceId: null,
    calledProcessDefinitionName: null,
  },
};

const renderPopover = () => {
  const {container} = render(<svg />);

  render(
    <PopoverOverlay selectedFlowNodeRef={container.querySelector('svg')} />,
    {
      wrapper: Wrapper,
    }
  );
};

describe('PopoverOverlay', () => {
  beforeEach(() => {
    flowNodeMetaDataStore.init();
    flowNodeSelectionStore.init();
    singleInstanceDiagramStore.init();
  });

  afterEach(() => {
    flowNodeMetaDataStore.reset();
    flowNodeSelectionStore.reset();
    currentInstanceStore.reset();
    incidentsStore.reset();
    singleInstanceDiagramStore.reset();
  });

  it('should render meta data for incident flow node', async () => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      ),
      rest.post(
        `/api/process-instances/${FLOW_NODE_ID}/flow-node-metadata`,
        (_, res, ctx) => res.once(ctx.json(incidentFlowNodeMetaData))
      ),
      rest.get(
        `http://localhost/api/process-instances/${FLOW_NODE_ID}/incidents`,
        (_, res, ctx) => res.once(ctx.json(mockIncidents))
      )
    );
    currentInstanceStore.setCurrentInstance(
      createInstance({
        id: FLOW_NODE_ID,
        state: 'INCIDENT',
      })
    );
    incidentsStore.init();

    flowNodeSelectionStore.selectFlowNode({flowNodeId: FLOW_NODE_ID});

    renderPopover();

    await screen.findByText(/Flow Node Instance Id/);
    expect(screen.getByText(/Start Date/)).toBeInTheDocument();
    expect(screen.getByText(/End Date/)).toBeInTheDocument();
    expect(screen.getByText(/Type/)).toBeInTheDocument();
    expect(screen.getByText(/Error Message/)).toBeInTheDocument();
    expect(screen.getByText(/View/)).toBeInTheDocument();
    expect(screen.queryByText(/Called Instance/)).not.toBeInTheDocument();

    const {incidentErrorMessage, flowNodeInstanceId} =
      incidentFlowNodeMetaData.instanceMetadata;

    expect(screen.getByText(flowNodeInstanceId)).toBeInTheDocument();
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(screen.getByText('No more retries left')).toBeInTheDocument();
    expect(screen.getByText(incidentErrorMessage)).toBeInTheDocument();
  });

  it('should render meta data for completed flow node', async () => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockCallActivityProcessXML))
      ),
      rest.post(
        `/api/process-instances/${CALL_ACTIVITY_FLOW_NODE_ID}/flow-node-metadata`,
        (_, res, ctx) => res.once(ctx.json(completedFlowNodeMetaData))
      )
    );
    currentInstanceStore.setCurrentInstance(
      createInstance({
        id: CALL_ACTIVITY_FLOW_NODE_ID,
        state: 'ACTIVE',
      })
    );
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: CALL_ACTIVITY_FLOW_NODE_ID,
    });

    renderPopover();

    await screen.findByText(/Flow Node Instance Id/);
    expect(screen.getByText(/Start Date/)).toBeInTheDocument();
    expect(screen.getByText(/End Date/)).toBeInTheDocument();
    expect(screen.getByText(/Called Instance/)).toBeInTheDocument();
    expect(screen.getByText(/View/)).toBeInTheDocument();

    expect(
      screen.getByText(
        completedFlowNodeMetaData.instanceMetadata.flowNodeInstanceId
      )
    ).toBeInTheDocument();
    expect(screen.getAllByText(MOCK_TIMESTAMP)).toHaveLength(2);
    expect(
      screen.getByText(
        `Called Process - ${completedFlowNodeMetaData.instanceMetadata.calledProcessInstanceId}`
      )
    ).toBeInTheDocument();

    expect(screen.queryByText(/incidentErrorType/)).not.toBeInTheDocument();
    expect(screen.queryByText(/incidentErrorMessage/)).not.toBeInTheDocument();
  });

  it('should render meta data modal', async () => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockCallActivityProcessXML))
      ),
      rest.post(
        `/api/process-instances/${CALL_ACTIVITY_FLOW_NODE_ID}/flow-node-metadata`,
        (_, res, ctx) => res.once(ctx.json(completedFlowNodeMetaData))
      )
    );
    currentInstanceStore.setCurrentInstance(
      createInstance({
        id: CALL_ACTIVITY_FLOW_NODE_ID,
        state: 'ACTIVE',
      })
    );
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: CALL_ACTIVITY_FLOW_NODE_ID,
    });

    renderPopover();

    await screen.findByText(/Flow Node Instance Id/);

    userEvent.click(screen.getByText(/View/));

    expect(
      screen.getByText(/Flow Node "Activity_0zqism7" Metadata/)
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Close Modal'})
    ).toBeInTheDocument();

    expect(
      screen.getByText(/"flowNodeId": "Activity_0zqism7"/)
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"flowNodeInstanceId": "2251799813699889"/)
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"flowNodeType": "TASK_CALL_ACTIVITY"/)
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"startDate": "2018-12-12 00:00:00"/)
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"endDate": "2018-12-12 00:00:00"/)
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"jobDeadline": "2018-12-12 00:00:00"/)
    ).toBeInTheDocument();
    expect(screen.getByText(/"incidentErrorType": null/)).toBeInTheDocument();
    expect(
      screen.getByText(/"incidentErrorMessage": null/)
    ).toBeInTheDocument();
    expect(screen.getByText(/"jobId": null/)).toBeInTheDocument();
    expect(screen.getByText(/"jobType": null/)).toBeInTheDocument();
    expect(screen.getByText(/"jobRetries": null/)).toBeInTheDocument();
    expect(screen.getByText(/"jobWorker": null/)).toBeInTheDocument();
    expect(screen.getByText(/"jobCustomHeaders": null/)).toBeInTheDocument();
    expect(
      screen.getByText(/"calledProcessInstanceId": "229843728748927482"/)
    ).toBeInTheDocument();
  });
});
