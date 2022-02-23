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
import {MemoryRouter} from 'react-router-dom';
import {incidentsStore} from 'modules/stores/incidents';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';

const FLOW_NODE_ID = 'StartEvent_1'; // this need to match the id from mockProcessXML
const CALL_ACTIVITY_FLOW_NODE_ID = 'Activity_0zqism7'; // this need to match the id from mockCallActivityProcessXML
const FLOW_NODE_INSTANCE_ID = '2251799813699889';
const PROCESS_INSTANCE_ID = '2251799813685591';

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
  incident: null,
  instanceMetadata: {
    flowNodeId: CALL_ACTIVITY_FLOW_NODE_ID,
    flowNodeInstanceId: FLOW_NODE_INSTANCE_ID,
    flowNodeType: 'TASK_CALL_ACTIVITY',
    startDate: '2021-03-26T09:50:22.457+0000',
    endDate: '2021-03-26T11:00:00.000+0000',
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

const multiInstanceCallActivityMetaData = {
  ...completedFlowNodeMetaData,
  flowNodeType: 'MULTI_INSTANCE_BODY',
};

const incidentFlowNodeMetaData = {
  flowNodeInstanceId: FLOW_NODE_INSTANCE_ID,
  flowNodeId: null,
  flowNodeType: null,
  instanceCount: null,
  breadcrumb: [],
  incident: {
    errorType: {id: 'JOB_NO_RETRIES', name: 'No more retries left.'},
    errorMessage: 'There are no more retries left.',
    rootCauseInstance: {
      instanceId: '00000000000000',
      processDefinitionId: '111111111111111',
      processDefinitionName: 'Called Process',
    },
  },
  incidentCount: 1,
  instanceMetadata: {
    flowNodeId: FLOW_NODE_ID,
    flowNodeInstanceId: FLOW_NODE_INSTANCE_ID,
    flowNodeType: 'START_EVENT',
    startDate: '2021-03-26T10:00:00.000+0000',
    endDate: null,
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

const rootIncidentFlowNodeMetaData = {
  ...incidentFlowNodeMetaData,
  incident: {
    ...incidentFlowNodeMetaData.incident,
    rootCauseInstance: {
      ...incidentFlowNodeMetaData.incident.rootCauseInstance,
      instanceId: PROCESS_INSTANCE_ID,
    },
  },
};

const multiInstanceFlowNodeData = {
  flowNodeInstanceId: null,
  flowNodeId: FLOW_NODE_ID,
  flowNodeType: 'START_EVENT',
  instanceCount: 10,
  breadcrumb: [],
  instanceMetadata: null,
  incidentCount: 3,
  incident: null,
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
        `/api/process-instances/${PROCESS_INSTANCE_ID}/flow-node-metadata`,
        (_, res, ctx) => res.once(ctx.json(incidentFlowNodeMetaData))
      ),
      rest.get(
        `/api/process-instances/${PROCESS_INSTANCE_ID}/incidents`,
        (_, res, ctx) => res.once(ctx.json(mockIncidents))
      )
    );
    currentInstanceStore.setCurrentInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'INCIDENT',
      })
    );
    incidentsStore.init();

    flowNodeSelectionStore.selectFlowNode({flowNodeId: FLOW_NODE_ID});

    renderPopover();

    expect(
      await screen.findByText(/Flow Node Instance Id/)
    ).toBeInTheDocument();
    expect(screen.getByText(/Start Date/)).toBeInTheDocument();
    expect(screen.getByText(/End Date/)).toBeInTheDocument();
    expect(screen.getByText(/Type/)).toBeInTheDocument();
    expect(screen.getByText(/Error Message/)).toBeInTheDocument();
    expect(screen.getAllByText(/View/)).toHaveLength(2);
    expect(screen.queryByText(/Called Instance/)).not.toBeInTheDocument();

    const {
      incident,
      instanceMetadata: {flowNodeInstanceId},
    } = incidentFlowNodeMetaData;

    expect(screen.getByText(flowNodeInstanceId)).toBeInTheDocument();
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(screen.getByText(incident.errorMessage)).toBeInTheDocument();
    expect(screen.getByText(incident.errorType.name)).toBeInTheDocument();
    expect(
      screen.getByText(
        `${incident.rootCauseInstance.processDefinitionName} - ${incident.rootCauseInstance.instanceId}`
      )
    );
  });

  it('should render meta data for completed flow node', async () => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockCallActivityProcessXML))
      ),
      rest.post(
        `/api/process-instances/${PROCESS_INSTANCE_ID}/flow-node-metadata`,
        (_, res, ctx) => res.once(ctx.json(completedFlowNodeMetaData))
      )
    );
    currentInstanceStore.setCurrentInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      })
    );
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: CALL_ACTIVITY_FLOW_NODE_ID,
    });

    renderPopover();

    expect(
      await screen.findByText(/Flow Node Instance Id/)
    ).toBeInTheDocument();
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
        `/api/process-instances/${PROCESS_INSTANCE_ID}/flow-node-metadata`,
        (_, res, ctx) => res.once(ctx.json(completedFlowNodeMetaData))
      )
    );
    currentInstanceStore.setCurrentInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      })
    );
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: CALL_ACTIVITY_FLOW_NODE_ID,
    });

    renderPopover();

    expect(
      await screen.findByText(/Flow Node Instance Id/)
    ).toBeInTheDocument();

    userEvent.click(screen.getAllByText(/View/)[0]);

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

  it('should render metadata for multi instance flow nodes', async () => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      ),
      rest.post(
        `/api/process-instances/:processInstanceId/flow-node-metadata`,
        (_, res, ctx) => res.once(ctx.json(multiInstanceFlowNodeData))
      )
    );
    currentInstanceStore.setCurrentInstance(
      createInstance({
        id: '123',
        state: 'ACTIVE',
      })
    );
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: FLOW_NODE_ID,
    });

    renderPopover();

    expect(
      await screen.findByText(/There are 10 Instances/)
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /To view details for any of these,\s*select one Instance in the Instance History./
      )
    ).toBeInTheDocument();
    expect(screen.getByText(/3 incidents occured/)).toBeInTheDocument();
    expect(screen.getByText(/View/)).toBeInTheDocument();
    expect(screen.queryByText(/Flow Node Instance Id/)).not.toBeInTheDocument();
  });

  it('should not render called instances for multi instance call activities', async () => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      ),
      rest.post(
        `/api/process-instances/:processInstanceId/flow-node-metadata`,
        (_, res, ctx) => res.once(ctx.json(multiInstanceCallActivityMetaData))
      )
    );
    currentInstanceStore.setCurrentInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      })
    );
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: CALL_ACTIVITY_FLOW_NODE_ID,
    });

    renderPopover();

    expect(
      await screen.findByText(/Flow Node Instance Id/)
    ).toBeInTheDocument();
    expect(screen.queryByText(/Called Instance/)).not.toBeInTheDocument();
  });

  it('should not render root cause instance link when instance is root', async () => {
    const {rootCauseInstance} = rootIncidentFlowNodeMetaData.incident;

    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      ),
      rest.post(
        `/api/process-instances/${PROCESS_INSTANCE_ID}/flow-node-metadata`,
        (_, res, ctx) => res.once(ctx.json(rootIncidentFlowNodeMetaData))
      ),
      rest.get(
        `/api/process-instances/${PROCESS_INSTANCE_ID}/incidents`,
        (_, res, ctx) => res.once(ctx.json(mockIncidents))
      )
    );
    currentInstanceStore.setCurrentInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'INCIDENT',
      })
    );
    incidentsStore.init();

    flowNodeSelectionStore.selectFlowNode({flowNodeId: FLOW_NODE_ID});

    renderPopover();

    expect(await screen.findByText(/Root Cause Instance/)).toBeInTheDocument();
    expect(screen.getByText(/Current Instance/)).toBeInTheDocument();
    expect(
      screen.queryByText(
        `${rootCauseInstance.processDefinitionName} - ${rootCauseInstance.instanceId}`
      )
    ).not.toBeInTheDocument();
  });
});
