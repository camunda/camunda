/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {FlowNodeInstancesTree} from '.';

import {
  eventSubProcessFlowNodeInstances,
  mockFlowNodeInstance,
  processInstanceId,
  Wrapper,
  eventSubprocessProcessInstance,
  mockEventSubprocessInstance,
} from './mocks';
import {eventSubProcess} from 'modules/testUtils';
import {createRef} from 'react';
import {mockFetchFlowNodeInstances} from 'modules/mocks/api/fetchFlowNodeInstances';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessInstance as mockFetchProcessInstanceDeprecated} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';

describe('FlowNodeInstancesTree - Event Subprocess', () => {
  beforeEach(async () => {
    mockFetchProcessInstance().withSuccess(mockEventSubprocessInstance);
    mockFetchProcessInstanceDeprecated().withSuccess(
      eventSubprocessProcessInstance,
    );
    mockFetchProcessInstanceDeprecated().withSuccess(
      eventSubprocessProcessInstance,
    );
    mockFetchProcessDefinitionXml().withSuccess(eventSubProcess);
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: [],
    });

    processInstanceDetailsStore.init({id: processInstanceId});
    flowNodeInstanceStore.init();
  });

  it('should be able to unfold and fold event subprocesses', async () => {
    mockFetchFlowNodeInstances().withSuccess(
      eventSubProcessFlowNodeInstances.level1,
    );

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    const {user} = render(
      <FlowNodeInstancesTree
        flowNodeInstance={{...mockFlowNodeInstance, state: 'ACTIVE'}}
        scrollableContainerRef={createRef<HTMLElement>()}
        isRoot
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      screen.queryByLabelText('Event Subprocess', {
        selector: "[aria-expanded='true']",
      }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('Interrupting timer')).not.toBeInTheDocument();

    mockFetchFlowNodeInstances().withSuccess(
      eventSubProcessFlowNodeInstances.level2,
    );

    await user.type(
      await screen.findByLabelText('Event Subprocess', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );

    expect(
      await screen.findByLabelText('Event Subprocess', {
        selector: "[aria-expanded='true']",
      }),
    ).toBeInTheDocument();

    expect(await screen.findByText('Interrupting timer')).toBeInTheDocument();

    await user.type(
      screen.getByLabelText('Event Subprocess', {
        selector: "[aria-expanded='true']",
      }),
      '{arrowleft}',
    );

    expect(screen.queryByText('Interrupting timer')).not.toBeInTheDocument();
  });
});
