/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {MetadataPopover} from '.';
import {
  createInstance,
  mockCallActivityProcessXML,
  mockProcessXML,
} from 'modules/testUtils';
import {mockIncidents} from 'modules/mocks/incidents';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {incidentsStore} from 'modules/stores/incidents';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {
  calledDecisionMetadata,
  calledFailedDecisionMetadata,
  calledInstanceMetadata,
  calledUnevaluatedDecisionMetadata,
  incidentFlowNodeMetaData,
  multiInstanceCallActivityMetadata,
  multiInstancesMetadata,
  rootIncidentFlowNodeMetaData,
  CALL_ACTIVITY_FLOW_NODE_ID,
  PROCESS_INSTANCE_ID,
  FLOW_NODE_ID,
} from 'modules/mocks/metadata';
import {metadataDemoProcess} from 'modules/mocks/metadataDemoProcess';
import {LocationLog} from 'modules/utils/LocationLog';
import {mockFetchProcessInstanceIncidents} from 'modules/mocks/api/processInstances/fetchProcessInstanceIncidents';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {useEffect} from 'react';

const MOCK_EXECUTION_DATE = '21 seconds';

jest.mock('date-fns', () => ({
  ...jest.requireActual('date-fns'),
  formatDistanceToNowStrict: () => MOCK_EXECUTION_DATE,
}));

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    return () => {
      flowNodeMetaDataStore.reset();
      flowNodeSelectionStore.reset();
      processInstanceDetailsStore.reset();
      incidentsStore.reset();
      processInstanceDetailsDiagramStore.reset();
    };
  }, []);

  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/processes/1']}>
        <Routes>
          <Route path="/processes/:processInstanceId" element={children} />
          <Route path="/decisions/:decisionInstanceId" element={<></>} />
        </Routes>
        <LocationLog />
      </MemoryRouter>
    </ThemeProvider>
  );
};

const renderPopover = () => {
  const {container} = render(<svg />);

  return render(
    <MetadataPopover selectedFlowNodeRef={container.querySelector('svg')} />,
    {
      wrapper: Wrapper,
    }
  );
};

describe('MetadataPopover', () => {
  beforeEach(() => {
    flowNodeMetaDataStore.init();
    flowNodeSelectionStore.init();
    processInstanceDetailsDiagramStore.init();
  });

  it('should render meta data for incident flow node', async () => {
    mockFetchProcessXML().withSuccess(mockProcessXML);
    mockFetchFlowNodeMetadata().withSuccess(incidentFlowNodeMetaData);
    mockFetchProcessInstanceIncidents().withSuccess(mockIncidents);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'INCIDENT',
      })
    );
    incidentsStore.init();

    flowNodeSelectionStore.selectFlowNode({flowNodeId: FLOW_NODE_ID});

    renderPopover();

    expect(
      await screen.findByText(/Flow Node Instance Key/)
    ).toBeInTheDocument();
    expect(screen.getByText(/Execution Duration/)).toBeInTheDocument();
    expect(screen.getByText(/Type/)).toBeInTheDocument();
    expect(screen.getByText(/Error Message/)).toBeInTheDocument();
    expect(screen.getAllByText(/View/)).toHaveLength(2);
    expect(
      screen.queryByText(/Called Process Instance/)
    ).not.toBeInTheDocument();

    const {incident, instanceMetadata} = incidentFlowNodeMetaData;

    expect(
      screen.getByText(instanceMetadata!.flowNodeInstanceId)
    ).toBeInTheDocument();
    expect(
      screen.getByText(`${MOCK_EXECUTION_DATE} (running)`)
    ).toBeInTheDocument();
    expect(screen.getByText(incident.errorMessage)).toBeInTheDocument();
    expect(screen.getByText(incident.errorType.name)).toBeInTheDocument();
    expect(
      screen.getByText(
        `${incident.rootCauseInstance.processDefinitionName} - ${incident.rootCauseInstance.instanceId}`
      )
    );
  });

  it('should render meta data for completed flow node', async () => {
    mockFetchProcessXML().withSuccess(mockCallActivityProcessXML);
    mockFetchFlowNodeMetadata().withSuccess(calledInstanceMetadata);

    processInstanceDetailsStore.setProcessInstance(
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
      await screen.findByText(/Flow Node Instance Key/)
    ).toBeInTheDocument();
    expect(screen.getByText(/Execution Duration/)).toBeInTheDocument();
    expect(screen.getByText(/Called Process Instance/)).toBeInTheDocument();
    expect(screen.getByText(/View/)).toBeInTheDocument();

    expect(
      screen.getByText(
        calledInstanceMetadata.instanceMetadata!.flowNodeInstanceId
      )
    ).toBeInTheDocument();
    expect(screen.getByText('Less than 1 second')).toBeInTheDocument();
    expect(screen.getByTestId('called-process-instance')).toHaveTextContent(
      `Called Process - ${
        calledInstanceMetadata.instanceMetadata!.calledProcessInstanceId
      }`
    );
    expect(screen.queryByText(/incidentErrorType/)).not.toBeInTheDocument();
    expect(screen.queryByText(/incidentErrorMessage/)).not.toBeInTheDocument();
  });

  it('should render meta data modal', async () => {
    mockFetchProcessXML().withSuccess(mockCallActivityProcessXML);
    mockFetchFlowNodeMetadata().withSuccess(calledInstanceMetadata);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      })
    );
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: CALL_ACTIVITY_FLOW_NODE_ID,
    });

    const {user} = renderPopover();

    expect(
      await screen.findByText(/Flow Node Instance Key/)
    ).toBeInTheDocument();

    const [firstViewLink] = screen.getAllByText(/View/);
    expect(firstViewLink).toBeInTheDocument();

    await user.click(firstViewLink!);

    expect(
      screen.getByText(/Flow Node "Activity_0zqism7" 2251799813699889 Metadata/)
    ).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /close/i})).toBeInTheDocument();

    expect(
      await screen.findByText(/"flowNodeId": "Activity_0zqism7"/)
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"flowNodeInstanceKey": "2251799813699889"/)
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
      screen.getByText(/"calledProcessInstanceKey": "229843728748927482"/)
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Close'}));
    await waitForElementToBeRemoved(
      screen.getByText(/Flow Node "Activity_0zqism7" 2251799813699889 Metadata/)
    );
  });

  it('should render metadata for multi instance flow nodes', async () => {
    mockFetchProcessXML().withSuccess(mockProcessXML);
    mockFetchFlowNodeMetadata().withSuccess(multiInstancesMetadata);

    processInstanceDetailsStore.setProcessInstance(
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
      await screen.findByText(/This Flow Node triggered 10 times/)
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /To view details for any of these, select one Instance in the Instance History./
      )
    ).toBeInTheDocument();
    expect(screen.getByText(/3 incidents occurred/)).toBeInTheDocument();
    expect(screen.getByText(/View/)).toBeInTheDocument();
    expect(
      screen.queryByText(/Flow Node Instance Key/)
    ).not.toBeInTheDocument();
  });

  it('should not render called instances for multi instance call activities', async () => {
    mockFetchProcessXML().withSuccess(mockProcessXML);
    mockFetchFlowNodeMetadata().withSuccess(multiInstanceCallActivityMetadata);

    processInstanceDetailsStore.setProcessInstance(
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
      await screen.findByText(/Flow Node Instance Key/)
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/Called Process Instance/)
    ).not.toBeInTheDocument();
  });

  it('should not render root cause instance link when instance is root', async () => {
    const {rootCauseInstance} = rootIncidentFlowNodeMetaData.incident;

    mockFetchProcessXML().withSuccess(mockProcessXML);
    mockFetchFlowNodeMetadata().withSuccess(rootIncidentFlowNodeMetaData);

    mockFetchProcessInstanceIncidents().withSuccess(mockIncidents);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'INCIDENT',
      })
    );
    incidentsStore.init();

    flowNodeSelectionStore.selectFlowNode({flowNodeId: FLOW_NODE_ID});

    renderPopover();

    expect(
      await screen.findByText(/Root Cause Process Instance/)
    ).toBeInTheDocument();
    expect(screen.getByText(/Current Instance/)).toBeInTheDocument();
    expect(
      screen.queryByText(
        `${rootCauseInstance.processDefinitionName} - ${rootCauseInstance.instanceId}`
      )
    ).not.toBeInTheDocument();
  });

  it('should render completed decision', async () => {
    jest.useFakeTimers();
    const {instanceMetadata} = calledDecisionMetadata;

    mockFetchProcessXML().withSuccess(metadataDemoProcess);
    mockFetchFlowNodeMetadata().withSuccess(calledDecisionMetadata);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'COMPLETED',
      })
    );

    flowNodeSelectionStore.selectFlowNode({flowNodeId: 'BusinessRuleTask'});

    const {user} = renderPopover();

    expect(await screen.findByText(/called decision/i)).toBeInTheDocument();
    expect(screen.queryByText(/incident/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/root cause decision/i)).not.toBeInTheDocument();

    await user.click(
      screen.getByText(
        `${instanceMetadata!.calledDecisionDefinitionName} - ${
          instanceMetadata!.calledDecisionInstanceId
        }`
      )
    );

    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent(
        `/decisions/${instanceMetadata!.calledDecisionInstanceId}`
      )
    );

    jest.clearAllTimers();
    jest.useFakeTimers();
  });

  it('should render failed decision', async () => {
    jest.useFakeTimers();

    const {instanceMetadata} = calledFailedDecisionMetadata;
    const {rootCauseDecision} = calledFailedDecisionMetadata!.incident!;

    mockFetchProcessXML().withSuccess(metadataDemoProcess);
    mockFetchFlowNodeMetadata().withSuccess(calledFailedDecisionMetadata);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'INCIDENT',
      })
    );

    flowNodeSelectionStore.selectFlowNode({flowNodeId: 'BusinessRuleTask'});

    const {user} = renderPopover();

    expect(await screen.findByText(/called decision/i)).toBeInTheDocument();
    expect(screen.getByText(/incident/i)).toBeInTheDocument();
    expect(
      screen.getByText(
        `${instanceMetadata!.calledDecisionDefinitionName} - ${
          instanceMetadata!.calledDecisionInstanceId
        }`
      )
    ).toBeInTheDocument();
    expect(screen.getByText(/root cause decision/i)).toBeInTheDocument();
    expect(screen.queryByText(/root cause instance/i)).not.toBeInTheDocument();

    await user.click(
      screen.getByText(
        `${rootCauseDecision!.decisionName!} - ${rootCauseDecision!.instanceId}`
      )
    );
    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent(
        `/decisions/${rootCauseDecision!.instanceId}`
      )
    );

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should render unevaluated decision', async () => {
    const {instanceMetadata} = calledUnevaluatedDecisionMetadata;

    mockFetchProcessXML().withSuccess(metadataDemoProcess);
    mockFetchFlowNodeMetadata().withSuccess(calledUnevaluatedDecisionMetadata);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      })
    );

    flowNodeSelectionStore.selectFlowNode({flowNodeId: 'BusinessRuleTask'});

    renderPopover();

    expect(await screen.findByText(/called decision/i)).toBeInTheDocument();
    expect(
      screen.getByText(instanceMetadata.calledDecisionDefinitionName)
    ).toBeInTheDocument();
    expect(screen.queryByText(/incident/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/root cause decision/i)).not.toBeInTheDocument();
  });
});
