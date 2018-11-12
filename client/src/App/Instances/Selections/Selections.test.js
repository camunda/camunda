import React from 'react';
import {shallow} from 'enzyme';

import {
  mockResolvedAsyncFn,
  xTimes,
  flushPromises,
  createSelection
} from 'modules/testUtils';

import {OPERATION_TYPE} from 'modules/constants';

import Selections from './Selections';

import * as api from 'modules/api/instances/instances';

import * as Styled from './styled';

api.applyOperation = mockResolvedAsyncFn();

const mockSelections = [];

describe('Selections', () => {
  let node;
  let mockonStateChange;
  let storeStateLocally;
  let selectionId;
  beforeEach(() => {
    mockonStateChange = jest.fn();
    storeStateLocally = jest.fn();

    xTimes(2)(index =>
      mockSelections.push(
        createSelection({
          selectionId: index,
          totalCount: 3
        })
      )
    );

    selectionId = mockSelections[0].selectionId;

    node = shallow(
      <Selections
        openSelection={null}
        selections={mockSelections}
        rollingSelectionIndex={1}
        selectionCount={2}
        instancesInSelectionsCount={6}
        onStateChange={mockonStateChange}
        storeStateLocally={storeStateLocally}
      />
    );
  });

  it('should match this snapshot', () => {
    expect(node).toMatchSnapshot();
  });

  it('should use CollapsablePanelConsumer', () => {
    expect(node.find('CollapsablePanelConsumer').length).toBe(1);
  });

  it('should render a CollapsablePanel', () => {
    const CollapsablePanelNode = node
      .find('CollapsablePanelConsumer')
      .dive()
      .find(Styled.CollapsablePanel);

    expect(CollapsablePanelNode.length).toBe(1);
    expect(CollapsablePanelNode).toMatchSnapshot();
  });

  it('should close a selection', () => {
    //given
    const defaultSelectionId = null;
    //when
    node.instance().handleToggleSelection(defaultSelectionId);

    //then
    expect(node.instance().props.onStateChange).toBeCalledWith({
      openSelection: null
    });
  });

  it('should open a selection when non is open', () => {
    // given
    node.setProps({openSelection: null});
    // when
    node.instance().handleToggleSelection(selectionId);

    // then
    expect(node.instance().props.onStateChange).toBeCalledWith({
      openSelection: selectionId
    });
  });

  it('should delete a selection', () => {
    //when
    node.instance().handleDeleteSelection(selectionId);

    //then
    expect(node.instance().props.onStateChange).toBeCalledWith({
      selections: mockSelections,
      instancesInSelectionsCount: 3,
      selectionCount: 1
    });
  });

  it('retry instances in a selection', async () => {
    //given
    node.setProps({openSelection: selectionId});

    //when
    node.instance().handleRetrySelection(selectionId);

    // when data fetched
    await flushPromises();

    //then
    expect(api.applyOperation).toHaveBeenCalledWith(
      OPERATION_TYPE.UPDATE_RETRIES,
      mockSelections[0].queries
    );
  });

  it('cancel instances in a selection', async () => {
    //when
    node.instance().handleCancelSelection(selectionId);

    // when data fetched
    await flushPromises();

    //then
    expect(api.applyOperation).toHaveBeenCalledWith(
      OPERATION_TYPE.CANCEL,
      mockSelections[0].queries
    );
  });
});
