/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {act, screen} from 'modules/testing-library';
import {modificationsStore} from 'modules/stores/modifications';
import {renderPopover} from './mocks';
import {open} from 'modules/mocks/diagrams';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {selectFlowNode} from 'modules/utils/flowNodeSelection';

describe('selectedRunningInstanceCount', () => {
  beforeEach(() => {
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: [
        {
          elementId: 'StartEvent_1',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
        {
          elementId: 'service-task-1',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
        {
          elementId: 'multi-instance-subprocess',
          active: 0,
          canceled: 0,
          incidents: 1,
          completed: 0,
        },
        {
          elementId: 'subprocess-start-1',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
        {
          elementId: 'subprocess-service-task',
          active: 0,
          canceled: 0,
          incidents: 1,
          completed: 0,
        },
        {
          elementId: 'service-task-7',
          active: 1,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
        {
          elementId: 'message-boundary',
          active: 1,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
      ],
    });

    mockFetchProcessDefinitionXml().withSuccess(
      open('diagramForModifications.bpmn'),
    );
  });

  afterEach(async () => {
    await new Promise(process.nextTick);
  });

  it('should not render when there are no running instances selected', async () => {
    modificationsStore.enableModificationMode();

    renderPopover();

    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'StartEvent_1',
        },
      );
    });

    expect(
      await screen.findByText(/Flow Node Modifications/),
    ).toBeInTheDocument();

    expect(
      screen.queryByText(/Selected running instances/),
    ).not.toBeInTheDocument();
  });

  it('should render when there are running instances selected', async () => {
    modificationsStore.enableModificationMode();

    renderPopover();

    mockFetchProcessDefinitionXml().withSuccess(
      open('diagramForModifications.bpmn'),
    );

    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'service-task-7',
        },
      );
    });

    expect(
      await screen.findByText(/Flow Node Modifications/),
    ).toBeInTheDocument();

    expect(
      await screen.findByText(/Selected running instances/),
    ).toBeInTheDocument();
  });
});
