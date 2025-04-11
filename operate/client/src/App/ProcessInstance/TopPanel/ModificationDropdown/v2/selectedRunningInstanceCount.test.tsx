/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {act, screen, waitFor} from 'modules/testing-library';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {modificationsStore} from 'modules/stores/modifications';
import {renderPopover} from './mocks';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {open} from 'modules/mocks/diagrams';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';

describe('selectedRunningInstanceCount', () => {
  beforeEach(() => {
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: [
        {
          flowNodeId: 'StartEvent_1',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
        {
          flowNodeId: 'service-task-1',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
        {
          flowNodeId: 'multi-instance-subprocess',
          active: 0,
          canceled: 0,
          incidents: 1,
          completed: 0,
        },
        {
          flowNodeId: 'subprocess-start-1',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
        {
          flowNodeId: 'subprocess-service-task',
          active: 0,
          canceled: 0,
          incidents: 1,
          completed: 0,
        },
        {
          flowNodeId: 'service-task-7',
          active: 1,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
        {
          flowNodeId: 'message-boundary',
          active: 1,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
      ],
    });

    mockFetchProcessXML().withSuccess(open('diagramForModifications.bpmn'));
    mockFetchProcessDefinitionXml().withSuccess(
      open('diagramForModifications.bpmn'),
    );
  });

  it.skip('should not render when there are no running instances selected', async () => {
    modificationsStore.enableModificationMode();

    renderPopover();

    await waitFor(() =>
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel,
      ).not.toBeNull(),
    );

    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'StartEvent_1',
      });
    });

    expect(
      await screen.findByText(/Flow Node Modifications/),
    ).toBeInTheDocument();

    expect(
      screen.queryByText(/Selected running instances/),
    ).not.toBeInTheDocument();
  });

  it.skip('should render when there are running instances selected', async () => {
    modificationsStore.enableModificationMode();

    renderPopover();

    await waitFor(() =>
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel,
      ).not.toBeNull(),
    );

    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'service-task-7',
      });
    });

    expect(
      await screen.findByText(/Flow Node Modifications/),
    ).toBeInTheDocument();

    expect(screen.getByText(/Selected running instances/)).toBeInTheDocument();
  });
});
