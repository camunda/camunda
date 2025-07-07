/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createRef} from 'react';
import {render, screen, waitFor, within} from 'modules/testing-library';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {multiInstanceProcess} from 'modules/testUtils';
import {FlowNodeInstancesTree} from '.';
import {
  multiInstanceProcessInstance,
  flowNodeInstances,
  mockFlowNodeInstance,
  processInstanceId,
  Wrapper,
  mockMultiInstanceProcessInstance,
} from './mocks';
import {mockFetchProcessInstance as mockFetchProcessInstanceDeprecated} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchFlowNodeInstances} from 'modules/mocks/api/fetchFlowNodeInstances';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';

describe('FlowNodeInstancesTree - Multi Instance Subprocess', () => {
  beforeEach(async () => {
    mockFetchProcessInstanceDeprecated().withSuccess(
      multiInstanceProcessInstance,
    );
    mockFetchProcessInstanceDeprecated().withSuccess(
      multiInstanceProcessInstance,
    );
    mockFetchProcessInstance().withSuccess(mockMultiInstanceProcessInstance);
    mockFetchProcessDefinitionXml().withSuccess(multiInstanceProcess);
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: [],
    });
  });

  afterEach(() => {
    flowNodeInstanceStore.reset();
    processInstanceDetailsStore.reset();
    flowNodeInstanceStore.reset();
    processInstanceDetailsStore.reset();
    flowNodeInstanceStore.reset();
  });

  it('should load the instance history', async () => {
    processInstanceDetailsStore.init({id: processInstanceId});
    flowNodeInstanceStore.init();

    mockFetchFlowNodeInstances().withSuccess(flowNodeInstances.level1);

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    render(
      <FlowNodeInstancesTree
        flowNodeInstance={mockFlowNodeInstance}
        scrollableContainerRef={createRef<HTMLElement>()}
        isRoot
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      await screen.findByText('Multi-Instance Process'),
    ).toBeInTheDocument();
    expect(screen.getByText('Peter Fork')).toBeInTheDocument();
    expect(
      screen.getByText('Filter-Map Sub Process (Multi Instance)'),
    ).toBeInTheDocument();
  });

  it('should be able to unfold and fold subprocesses', async () => {
    processInstanceDetailsStore.init({id: processInstanceId});
    flowNodeInstanceStore.init();

    mockFetchFlowNodeInstances().withSuccess(flowNodeInstances.level1);

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    const {user} = render(
      <FlowNodeInstancesTree
        flowNodeInstance={mockFlowNodeInstance}
        scrollableContainerRef={createRef<HTMLElement>()}
        isRoot
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      screen.queryByLabelText('Filter-Map Sub Process', {
        selector: "[aria-expanded='false']",
      }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('Start Filter-Map')).not.toBeInTheDocument();

    mockFetchFlowNodeInstances().withSuccess(flowNodeInstances.level2);

    await user.type(
      await screen.findByLabelText('Filter-Map Sub Process (Multi Instance)', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );

    expect(
      await screen.findByLabelText('Filter-Map Sub Process (Multi Instance)', {
        selector: "[aria-expanded='true']",
      }),
    ).toBeInTheDocument();

    expect(screen.queryByText('Start Filter-Map')).not.toBeInTheDocument();

    mockFetchFlowNodeInstances().withSuccess(flowNodeInstances.level3);

    await user.type(
      await screen.findByLabelText('Filter-Map Sub Process', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowRight}',
    );

    expect(await screen.findByText('Start Filter-Map')).toBeInTheDocument();
    expect(
      screen.getByLabelText('Filter-Map Sub Process (Multi Instance)', {
        selector: "[aria-expanded='true']",
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText('Filter-Map Sub Process', {
        selector: "[aria-expanded='true']",
      }),
    ).toBeInTheDocument();

    await user.type(
      screen.getByLabelText('Filter-Map Sub Process', {
        selector: "[aria-expanded='true']",
      }),
      '{arrowleft}',
    );

    expect(screen.queryByText('Start Filter-Map')).not.toBeInTheDocument();
  });

  it('should poll for instances on root level', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    processInstanceDetailsStore.init({id: processInstanceId});
    flowNodeInstanceStore.init();

    mockFetchFlowNodeInstances().withSuccess(flowNodeInstances.level1);

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    render(
      <FlowNodeInstancesTree
        flowNodeInstance={mockFlowNodeInstance}
        scrollableContainerRef={createRef<HTMLElement>()}
        isRoot
      />,
      {
        wrapper: Wrapper,
      },
    );

    const withinMultiInstanceFlowNode = within(
      screen.getByTestId(
        `tree-node-${
          flowNodeInstances.level1Poll[processInstanceId]!.children[1]!.id
        }`,
      ),
    );

    expect(
      await withinMultiInstanceFlowNode.findByTestId('INCIDENT-icon'),
    ).toBeInTheDocument();
    expect(
      withinMultiInstanceFlowNode.queryByTestId('COMPLETED-icon'),
    ).not.toBeInTheDocument();

    // poll request
    mockFetchProcessInstanceDeprecated().withSuccess(
      multiInstanceProcessInstance,
    );
    mockFetchFlowNodeInstances().withSuccess(flowNodeInstances.level1Poll);

    vi.runOnlyPendingTimers();

    expect(
      await withinMultiInstanceFlowNode.findByTestId('COMPLETED-icon'),
    ).toBeInTheDocument();
    expect(
      withinMultiInstanceFlowNode.queryByTestId('INCIDENT-icon'),
    ).not.toBeInTheDocument();

    vi.clearAllTimers();
    vi.useRealTimers();
  });
});
