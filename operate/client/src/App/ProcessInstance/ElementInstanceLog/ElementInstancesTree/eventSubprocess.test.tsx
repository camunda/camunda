/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createRef} from 'react';
import {render, screen} from 'modules/testing-library';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {ElementInstancesTree} from './index';
import {
  eventSubProcessElementInstances,
  processInstanceId,
  Wrapper,
  eventSubprocessProcessInstance,
  mockEventSubprocessInstance,
} from './mocks';
import {eventSubProcess} from 'modules/testUtils';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {mockQueryBatchOperationItems} from 'modules/mocks/api/v2/batchOperations/queryBatchOperationItems';
import {mockFetchProcessInstance as mockFetchProcessInstanceDeprecated} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';

describe('ElementInstancesTree - Event Subprocess', () => {
  beforeEach(async () => {
    mockFetchProcessInstanceDeprecated().withSuccess(
      eventSubprocessProcessInstance,
    );
    mockFetchProcessInstance().withSuccess(mockEventSubprocessInstance);
    mockFetchProcessDefinitionXml().withSuccess(eventSubProcess);
    mockFetchFlownodeInstancesStatistics().withSuccess({items: []});
    mockQueryBatchOperationItems().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
    mockSearchElementInstances().withSuccess(
      eventSubProcessElementInstances.level1,
    );

    processInstanceDetailsStore.init({id: processInstanceId});
  });

  afterEach(() => {
    processInstanceDetailsStore.reset();
  });

  it('should be able to unfold and fold event subprocesses', async () => {
    const {user} = render(
      <ElementInstancesTree
        processInstance={mockEventSubprocessInstance}
        scrollableContainerRef={createRef<HTMLDivElement>()}
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

    mockSearchElementInstances().withSuccess(
      eventSubProcessElementInstances.level2,
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
