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
import {createInstance} from 'modules/testUtils';
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';
import userEvent from '@testing-library/user-event';
import {MemoryRouter} from 'react-router';

const FLOW_NODE_ID = 'startEvent';
const FLOW_NODE_INSTANCE_ID = '2251799813686348';

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
    flowNodeId: FLOW_NODE_ID,
    flowNodeInstanceId: FLOW_NODE_INSTANCE_ID,
    flowNodeType: 'START_EVENT',
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
  },
};

jest.mock('./getPopoverPosition', () => ({
  getPopoverPosition: () => ({
    overlay: {
      top: 0,
      left: 0,
    },
    side: 'TOP',
  }),
}));

const renderPopover = () => {
  const {container} = render(<div />);

  const PopoverlayProps = {
    onOverlayAdd: (
      id: string,
      type: string,
      overlay: {html: HTMLDivElement}
    ) => {
      container.appendChild(overlay.html);
    },
    onOverlayClear: () => {},
    isViewerLoaded: true,
    diagramContainer: document.createElement('div'),
    flowNode: document.createElementNS('http://www.w3.org/2000/svg', 'svg'),
  };

  render(<PopoverOverlay {...PopoverlayProps} />, {wrapper: Wrapper});
};

describe('PopoverOverlay', () => {
  beforeEach(() => {
    flowNodeMetaDataStore.init();
    flowNodeSelectionStore.init();
    currentInstanceStore.setCurrentInstance(
      createInstance({
        id: FLOW_NODE_ID,
        state: 'ACTIVE',
      })
    );
  });

  afterEach(() => {
    flowNodeMetaDataStore.reset();
    flowNodeSelectionStore.reset();
    currentInstanceStore.reset();
  });

  it('should render meta data for incident flow node', async () => {
    mockServer.use(
      rest.post(
        `/api/process-instances/${FLOW_NODE_ID}/flow-node-metadata`,
        (_, res, ctx) => res.once(ctx.json(incidentFlowNodeMetaData))
      )
    );

    flowNodeSelectionStore.selectFlowNode({flowNodeId: FLOW_NODE_ID});

    renderPopover();

    await screen.findByText(/flowNodeInstanceId/);
    expect(screen.getByText(/jobId/)).toBeInTheDocument();
    expect(screen.getByText(/startDate/)).toBeInTheDocument();
    expect(screen.getByText(/endDate/)).toBeInTheDocument();
    expect(screen.getByText(/incidentErrorType/)).toBeInTheDocument();
    expect(screen.getByText(/incidentErrorMessage/)).toBeInTheDocument();
    expect(screen.getByText(/calledProcessInstanceId/)).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Show more metadata'})
    ).toBeInTheDocument();

    const {incidentErrorMessage, incidentErrorType, jobId, flowNodeInstanceId} =
      incidentFlowNodeMetaData.instanceMetadata;

    expect(screen.getByText(flowNodeInstanceId)).toBeInTheDocument();
    expect(screen.getByText(jobId)).toBeInTheDocument();
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(screen.getByText(incidentErrorType)).toBeInTheDocument();
    expect(screen.getByText(incidentErrorMessage)).toBeInTheDocument();
    expect(screen.getByText('None')).toBeInTheDocument();
  });

  it('should render meta data for completed flow node', async () => {
    mockServer.use(
      rest.post(
        `/api/process-instances/${FLOW_NODE_ID}/flow-node-metadata`,
        (_, res, ctx) => res.once(ctx.json(completedFlowNodeMetaData))
      )
    );

    flowNodeSelectionStore.selectFlowNode({flowNodeId: FLOW_NODE_ID});

    renderPopover();

    await screen.findByText(/flowNodeInstanceId/);
    expect(screen.getByText(/startDate/)).toBeInTheDocument();
    expect(screen.getByText(/endDate/)).toBeInTheDocument();
    expect(screen.getByText(/calledProcessInstanceId/)).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Show more metadata'})
    ).toBeInTheDocument();

    expect(
      screen.getByText(
        completedFlowNodeMetaData.instanceMetadata.flowNodeInstanceId
      )
    ).toBeInTheDocument();
    expect(screen.getAllByText(MOCK_TIMESTAMP)).toHaveLength(2);
    expect(
      screen.getByText(
        completedFlowNodeMetaData.instanceMetadata.calledProcessInstanceId
      )
    ).toBeInTheDocument();

    expect(screen.queryByText(/jobId/)).not.toBeInTheDocument();
    expect(screen.queryByText(/incidentErrorType/)).not.toBeInTheDocument();
    expect(screen.queryByText(/incidentErrorMessage/)).not.toBeInTheDocument();
  });

  it('should render meta data modal', async () => {
    mockServer.use(
      rest.post(
        `/api/process-instances/${FLOW_NODE_ID}/flow-node-metadata`,
        (_, res, ctx) => res.once(ctx.json(completedFlowNodeMetaData))
      )
    );

    flowNodeSelectionStore.selectFlowNode({flowNodeId: FLOW_NODE_ID});

    renderPopover();

    await screen.findByText(/flowNodeInstanceId/);

    userEvent.click(screen.getByRole('button', {name: 'Show more metadata'}));

    expect(
      screen.getByText(/Flow Node "startEvent" Metadata/)
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Close Modal'})
    ).toBeInTheDocument();

    expect(screen.getByText(/"flowNodeId": "startEvent"/)).toBeInTheDocument();
    expect(
      screen.getByText(/"flowNodeInstanceId": "2251799813686348"/)
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"flowNodeType": "START_EVENT"/)
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
