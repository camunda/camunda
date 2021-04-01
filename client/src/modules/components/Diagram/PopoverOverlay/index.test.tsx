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

const FLOW_NODE_ID = 'startEvent';
const FLOW_NODE_INSTANCE_ID = '2251799813686348';

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

  render(<PopoverOverlay {...PopoverlayProps} />, {wrapper: ThemeProvider});
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
    expect(
      screen.getByRole('button', {name: 'Show more metadata'})
    ).toBeInTheDocument();

    const {
      incidentErrorMessage,
      incidentErrorType,
      jobId,
      flowNodeInstanceId,
      startDate,
    } = incidentFlowNodeMetaData.instanceMetadata;

    expect(screen.getByText(flowNodeInstanceId)).toBeInTheDocument();
    expect(screen.getByText(jobId)).toBeInTheDocument();
    expect(screen.getByText(startDate)).toBeInTheDocument();
    expect(screen.getByText(incidentErrorType)).toBeInTheDocument();
    expect(screen.getByText(incidentErrorMessage)).toBeInTheDocument();
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
    expect(
      screen.getByRole('button', {name: 'Show more metadata'})
    ).toBeInTheDocument();

    const {
      flowNodeInstanceId,
      startDate,
    } = completedFlowNodeMetaData.instanceMetadata;

    expect(screen.getByText(flowNodeInstanceId)).toBeInTheDocument();
    expect(screen.getByText(startDate)).toBeInTheDocument();

    expect(screen.queryByText(/jobId/)).not.toBeInTheDocument();
    expect(screen.queryByText(/incidentErrorType/)).not.toBeInTheDocument();
    expect(screen.queryByText(/incidentErrorMessage/)).not.toBeInTheDocument();
  });
});
