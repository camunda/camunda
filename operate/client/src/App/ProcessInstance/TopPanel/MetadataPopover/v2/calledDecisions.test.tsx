/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {screen, waitFor} from 'modules/testing-library';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {labels, renderPopover} from './mocks';
import {
  calledDecisionMetadata,
  calledFailedDecisionMetadata,
  calledInstanceMetadata,
  calledUnevaluatedDecisionMetadata,
  CALL_ACTIVITY_FLOW_NODE_ID,
  PROCESS_INSTANCE_ID,
} from 'modules/mocks/metadata';
import {metadataDemoProcess} from 'modules/mocks/metadataDemoProcess';
import {
  createInstance,
  createProcessInstance,
  mockCallActivityProcessXML,
} from 'modules/testUtils';
import {init} from 'modules/utils/flowNodeMetadata';
import {selectFlowNode} from 'modules/utils/flowNodeSelection';
import {mockFetchProcessInstance as mockFetchProcessInstanceV2} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';

const MOCK_EXECUTION_DATE = '21 seconds';

vi.mock('date-fns', async () => {
  const actual = await vi.importActual('date-fns');
  return {
    ...actual,
    formatDistanceToNowStrict: () => MOCK_EXECUTION_DATE,
  };
});

describe('MetadataPopover', () => {
  beforeEach(() => {
    init('process-instance', []);
    flowNodeSelectionStore.init();
  });

  it('should render meta data for completed flow node', async () => {
    mockFetchProcessDefinitionXml().withSuccess(mockCallActivityProcessXML);
    mockFetchFlowNodeMetadata().withSuccess(calledInstanceMetadata);
    mockFetchProcessInstanceV2().withSuccess(createProcessInstance());

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );
    selectFlowNode(
      {},
      {
        flowNodeId: CALL_ACTIVITY_FLOW_NODE_ID,
      },
    );

    renderPopover();

    expect(
      await screen.findByText(labels.flowNodeInstanceKey),
    ).toBeInTheDocument();
    expect(screen.getByText(labels.executionDuration)).toBeInTheDocument();
    expect(
      await screen.findByText(labels.calledProcessInstance),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: labels.showMoreMetadata}),
    ).toBeInTheDocument();

    expect(
      screen.getByText(
        calledInstanceMetadata.instanceMetadata!.flowNodeInstanceId,
      ),
    ).toBeInTheDocument();
    expect(screen.getByText('Less than 1 second')).toBeInTheDocument();
    expect(screen.getByTestId('called-process-instance')).toHaveTextContent(
      `Called Process - ${
        calledInstanceMetadata.instanceMetadata!.calledProcessInstanceId
      }`,
    );
  });

  it('should render completed decision', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
    const {instanceMetadata} = calledDecisionMetadata;

    mockFetchProcessDefinitionXml().withSuccess(metadataDemoProcess);
    mockFetchFlowNodeMetadata().withSuccess(calledDecisionMetadata);
    mockFetchProcessInstanceV2().withSuccess(createProcessInstance());

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'COMPLETED',
      }),
    );

    selectFlowNode({}, {flowNodeId: 'BusinessRuleTask'});

    const {user} = renderPopover();

    expect(
      await screen.findByText(labels.calledDecisionInstance),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('heading', {name: labels.incident}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(labels.rootCauseDecisionInstance),
    ).not.toBeInTheDocument();

    await user.click(
      screen.getByText(
        `${instanceMetadata!.calledDecisionDefinitionName} - ${
          instanceMetadata!.calledDecisionInstanceId
        }`,
      ),
    );

    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent(
        `/decisions/${instanceMetadata!.calledDecisionInstanceId}`,
      ),
    );

    vi.clearAllTimers();
    vi.useFakeTimers();
  });

  it('should render failed decision', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    const {instanceMetadata} = calledFailedDecisionMetadata;
    const {rootCauseDecision} = calledFailedDecisionMetadata!.incident!;

    mockFetchProcessDefinitionXml().withSuccess(metadataDemoProcess);
    mockFetchFlowNodeMetadata().withSuccess(calledFailedDecisionMetadata);
    mockFetchProcessInstanceV2().withSuccess(createProcessInstance());

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'INCIDENT',
      }),
    );

    selectFlowNode({}, {flowNodeId: 'BusinessRuleTask'});

    const {user} = renderPopover();

    expect(
      await screen.findByText(labels.calledDecisionInstance),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {name: labels.incident}),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        `${instanceMetadata!.calledDecisionDefinitionName} - ${
          instanceMetadata!.calledDecisionInstanceId
        }`,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(labels.rootCauseDecisionInstance),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(labels.rootCauseProcessInstance),
    ).not.toBeInTheDocument();

    await user.click(
      screen.getByText(
        `${rootCauseDecision!.decisionName!} - ${
          rootCauseDecision!.instanceId
        }`,
      ),
    );
    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent(
        `/decisions/${rootCauseDecision!.instanceId}`,
      ),
    );

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should render unevaluated decision', async () => {
    mockFetchProcessDefinitionXml().withSuccess(metadataDemoProcess);
    mockFetchFlowNodeMetadata().withSuccess(calledUnevaluatedDecisionMetadata);
    mockFetchProcessInstanceV2().withSuccess(createProcessInstance());

    const {instanceMetadata} = calledUnevaluatedDecisionMetadata;

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );

    selectFlowNode({}, {flowNodeId: 'BusinessRuleTask'});

    renderPopover();

    expect(
      await screen.findByText(labels.calledDecisionInstance),
    ).toBeInTheDocument();
    expect(
      screen.getByText(instanceMetadata.calledDecisionDefinitionName),
    ).toBeInTheDocument();
    expect(screen.queryByText(labels.incident)).not.toBeInTheDocument();
    expect(
      screen.queryByText(labels.rootCauseDecisionInstance),
    ).not.toBeInTheDocument();
  });
});
