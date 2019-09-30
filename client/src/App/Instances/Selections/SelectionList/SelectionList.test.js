/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {
  createSelection,
  createInstance,
  mockResolvedAsyncFn,
  groupedWorkflowsMock,
  flushPromises
} from 'modules/testUtils';
import {ThemeProvider} from 'modules/theme';
import {DataManagerProvider} from 'modules/DataManager';
import {SelectionProvider} from 'modules/contexts/SelectionContext';
import {InstancesPollProvider} from 'modules/contexts/InstancesPollContext';
import * as instancesApi from 'modules/api/instances/instances';
import {formatGroupedWorkflows} from 'modules/utils/instance';
import {
  MESSAGES_TYPE,
  OPERATION_TYPE,
  FILTER_SELECTION
} from 'modules/constants';
import {MESSAGES} from 'modules/components/ContextualMessage/constants';

import SelectionList from './SelectionList';
import {NO_SELECTIONS_MESSAGE} from './constants';
jest.mock('modules/utils/bpmn');
instancesApi.applyBatchOperation = mockResolvedAsyncFn();

describe('SelectionList', () => {
  let node;
  beforeEach(async () => {
    node = mount(
      <DataManagerProvider>
        <ThemeProvider>
          <SelectionProvider
            groupedWorkflows={formatGroupedWorkflows(groupedWorkflowsMock)}
            filter={FILTER_SELECTION.incidents}
          >
            <InstancesPollProvider
              onSelectionsRefresh={jest.fn()}
              visibleIdsInListView={[1, 2, 3]}
              visibleIdsInSelections={[1, 2]}
            >
              <SelectionList />
            </InstancesPollProvider>
          </SelectionProvider>
        </ThemeProvider>
      </DataManagerProvider>
    );
  });

  it('should render an empty list of selections', () => {
    const liNodes = node.find('li[data-test="selection-list-item"]');

    // 2 list nodes
    expect(liNodes).toHaveLength(0);

    // empty message
    expect(
      node.find('div[data-test="empty-selection-list-message"]').text()
    ).toEqual(NO_SELECTIONS_MESSAGE);
  });

  it('should render list of selections', () => {
    // given
    const selections = [
      createSelection({
        selectionId: 1
      }),
      createSelection({
        selectionId: 2
      })
    ];
    const openSelection = 2;
    node.find('BasicSelectionProvider').setState({selections, openSelection});

    // then
    const liNodes = node.find('li[data-test="selection-list-item"]');

    // 2 list nodes
    expect(liNodes).toHaveLength(2);

    // the second selection is open
    expect(
      liNodes
        .at(0)
        .find('Selection')
        .prop('isOpen')
    ).toBe(false);
    expect(
      liNodes
        .at(1)
        .find('Selection')
        .prop('isOpen')
    ).toBe(true);

    // data props
    selections.forEach((selection, idx) => {
      expect(
        liNodes
          .at(idx)
          .find('Selection')
          .prop('selectionId')
      ).toBe(selection.selectionId);
      expect(
        liNodes
          .at(idx)
          .find('Selection')
          .prop('instances')
      ).toEqual(selection.instancesMap);
      expect(
        liNodes
          .at(idx)
          .find('Selection')
          .prop('instanceCount')
      ).toBe(selection.totalCount);
    });
  });

  it('should render contexual message when max. number of selections is reached', () => {
    // given
    let selections = [];
    for (let i = 1; i <= 10; i++) {
      selections.push(createSelection({selectionId: i}));
    }
    node.find('BasicSelectionProvider').setState({selections});

    // then
    const expectedMessage = MESSAGES[MESSAGES_TYPE.DROP_SELECTION];
    expect(
      node.find('div[data-test="contextual-message-test"]').text()
    ).toEqual(expectedMessage);
  });

  it('should retry a selection', async () => {
    // given
    const selections = [
      createSelection({
        selectionId: 1
      })
    ];
    const openSelection = 2;
    node.find('BasicSelectionProvider').setState({selections, openSelection});

    // when
    await node.find('Selection').prop('onRetry')();

    // then
    expect(instancesApi.applyBatchOperation).toHaveBeenCalledWith(
      OPERATION_TYPE.RESOLVE_INCIDENT,
      selections[0].queries
    );
  });

  it('should cancel a selection', async () => {
    // given
    const selections = [
      createSelection({
        selectionId: 1
      })
    ];
    const openSelection = 2;
    node.find('BasicSelectionProvider').setState({selections, openSelection});

    // when
    await node.find('Selection').prop('onCancel')();

    // then
    expect(instancesApi.applyBatchOperation).toHaveBeenCalledWith(
      OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE,
      selections[0].queries
    );
  });

  it('should send ids for polling after retry operation is started', async () => {
    const selections = [
      createSelection({
        selectionId: 1
      })
    ];
    const openSelection = 2;
    node.find('BasicSelectionProvider').setState({selections, openSelection});

    // when
    await node.find('Selection').prop('onRetry')();
    await flushPromises();
    node.update();

    // then
    expect(
      node.find(InstancesPollProvider.WrappedComponent).instance().state.active
    ).toEqual(new Set([...selections[0].instancesMap.keys()]));
  });

  it('should send ids for polling after cancel operation is started', async () => {
    const selections = [
      createSelection({
        selectionId: 1
      })
    ];
    const openSelection = 2;
    node.find('BasicSelectionProvider').setState({selections, openSelection});

    // when
    await node.find('Selection').prop('onCancel')();
    await flushPromises();
    node.update();

    // then
    expect(
      node.find(InstancesPollProvider.WrappedComponent).instance().state.active
    ).toEqual(new Set([...selections[0].instancesMap.keys()]));
  });

  it('should send active ids for polling if already present in selections', async () => {
    const instanceId = '100';
    const selections = [
      createSelection({
        selectionId: 1,
        instancesMap: new Map([
          [
            instanceId,
            createInstance({id: instanceId, hasActiveOperation: true})
          ]
        ])
      })
    ];

    node.find('BasicSelectionProvider').setState({selections});
    node.update();
    expect(
      node.find(InstancesPollProvider.WrappedComponent).instance().state.active
    ).toEqual(new Set([instanceId]));
  });
});
