/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createRef} from 'react';
import {render, screen} from 'modules/testing-library';
import {open} from 'modules/mocks/diagrams';
import {
  Wrapper,
  adHocSubProcessesInstance,
  adHocNodeElementInstances,
  mockAdHocSubProcessesInstance,
} from './mocks';
import {ElementInstancesTree} from './index';
import {mockFetchProcessInstance as mockFetchProcessInstanceDeprecated} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {mockQueryBatchOperationItems} from 'modules/mocks/api/v2/batchOperations/queryBatchOperationItems';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';

describe('ElementInstancesTree - Ad Hoc Sub Process', () => {
  beforeEach(async () => {
    mockFetchProcessInstanceDeprecated().withSuccess(adHocSubProcessesInstance);
    mockFetchProcessInstance().withSuccess(mockAdHocSubProcessesInstance);
    mockFetchProcessDefinitionXml().withSuccess(open('AdHocProcess.bpmn'));
    mockFetchFlownodeInstancesStatistics().withSuccess({items: []});
    mockQueryBatchOperationItems().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
    mockSearchElementInstances().withSuccess(adHocNodeElementInstances.level1);

    processInstanceDetailsStore.init({id: adHocSubProcessesInstance.id});
  });

  afterEach(() => {
    processInstanceDetailsStore.reset();
  });

  it('should be able to unfold and fold ad hoc sub processes', async () => {
    const {user} = render(
      <ElementInstancesTree
        processInstance={mockAdHocSubProcessesInstance}
        scrollableContainerRef={createRef<HTMLDivElement>()}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      screen.queryByLabelText('Ad Hoc Sub Process', {
        selector: "[aria-expanded='true']",
      }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('Task A')).not.toBeInTheDocument();

    mockSearchElementInstances().withSuccess(adHocNodeElementInstances.level2);

    await user.type(
      await screen.findByLabelText('Ad Hoc Sub Process', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );

    expect(
      await screen.findByLabelText('Ad Hoc Sub Process', {
        selector: "[aria-expanded='true']",
      }),
    ).toBeInTheDocument();

    expect(await screen.findByText('Task A')).toBeInTheDocument();

    await user.type(
      screen.getByLabelText('Ad Hoc Sub Process', {
        selector: "[aria-expanded='true']",
      }),
      '{arrowleft}',
    );

    expect(screen.queryByText('Task A')).not.toBeInTheDocument();
  });
});
