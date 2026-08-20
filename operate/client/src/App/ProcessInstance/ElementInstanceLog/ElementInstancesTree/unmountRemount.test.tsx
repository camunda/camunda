/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {act} from 'react';
import {render, screen} from 'modules/testing-library';
import {open} from 'modules/mocks/diagrams';
import {searchResult} from 'modules/testUtils';
import {
  getWrapper,
  adHocNodeElementInstances,
  mockAdHocSubProcessesInstance,
  parseBusinessObjects,
} from './mocks';
import {ElementInstancesTree} from './index';
import {elementInstancesTreeStore} from './elementInstancesTreeStore';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchElementInstancesStatistics} from 'modules/mocks/api/v2/elementInstances/elementInstancesStatistics/fetchElementInstancesStatistics';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {mockQueryBatchOperationItems} from 'modules/mocks/api/v2/batchOperations/queryBatchOperationItems';

const adHocProcessXml = open('AdHocProcess.bpmn');

const mockRenderDependencies = () => {
  mockFetchProcessInstance().withSuccess(mockAdHocSubProcessesInstance);
  mockFetchProcessDefinitionXml().withSuccess(adHocProcessXml);
  mockFetchElementInstancesStatistics().withSuccess({items: []});
  mockQueryBatchOperationItems().withSuccess(searchResult([]));
};

describe('ElementInstancesTree - unmount/remount', () => {
  it('keeps a previously expanded child node expanded after the component unmounts and remounts with the same process instance', async () => {
    const {businessObjects} = await parseBusinessObjects(adHocProcessXml);
    const adHocSubProcessScopeKey =
      adHocNodeElementInstances.level1.items[1]!.elementInstanceKey;

    mockRenderDependencies();
    mockSearchElementInstances().withSuccess(adHocNodeElementInstances.level1);

    const {unmount} = render(
      <ElementInstancesTree
        processInstance={mockAdHocSubProcessesInstance}
        businessObjects={businessObjects}
      />,
      {wrapper: getWrapper()},
    );

    expect(await screen.findByText('Ad Hoc Sub Process')).toBeInTheDocument();

    mockSearchElementInstances().withSuccess(adHocNodeElementInstances.level2);
    await act(async () => {
      await elementInstancesTreeStore.expandNode(adHocSubProcessScopeKey);
    });

    expect(await screen.findByText('Task A')).toBeInTheDocument();

    unmount();

    mockRenderDependencies();
    mockSearchElementInstances().withSuccess(adHocNodeElementInstances.level1);

    render(
      <ElementInstancesTree
        processInstance={mockAdHocSubProcessesInstance}
        businessObjects={businessObjects}
      />,
      {wrapper: getWrapper()},
    );

    expect(await screen.findByText('Ad Hoc Sub Process')).toBeInTheDocument();
    expect(screen.getByText('Task A')).toBeInTheDocument();
  });

  it('stops polling the root node once the component unmounts', async () => {
    const {businessObjects} = await parseBusinessObjects(adHocProcessXml);
    const rootScopeKey = mockAdHocSubProcessesInstance.processInstanceKey;

    vi.useFakeTimers({shouldAdvanceTime: true});

    mockRenderDependencies();
    mockSearchElementInstances().withSuccess(adHocNodeElementInstances.level1);

    const {unmount} = render(
      <ElementInstancesTree
        processInstance={mockAdHocSubProcessesInstance}
        businessObjects={businessObjects}
      />,
      {wrapper: getWrapper()},
    );

    expect(await screen.findByText('Ad Hoc Sub Process')).toBeInTheDocument();

    mockSearchElementInstances().withSuccess({
      ...adHocNodeElementInstances.level1,
      page: {...adHocNodeElementInstances.level1.page, totalItems: 999},
    });
    await act(() => vi.advanceTimersByTimeAsync(5000));

    expect(
      elementInstancesTreeStore.state.nodes.get(rootScopeKey)?.pageMetadata
        .totalItems,
    ).toBe(999);

    unmount();

    mockSearchElementInstances().withSuccess({
      ...adHocNodeElementInstances.level1,
      page: {...adHocNodeElementInstances.level1.page, totalItems: 111},
    });
    await act(() => vi.advanceTimersByTimeAsync(5000));

    expect(
      elementInstancesTreeStore.state.nodes.get(rootScopeKey)?.pageMetadata
        .totalItems,
    ).toBe(999);

    vi.clearAllTimers();
    vi.useRealTimers();
  });
});
