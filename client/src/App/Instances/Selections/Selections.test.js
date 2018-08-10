import React from 'react';
import {shallow} from 'enzyme';

import {mockResolvedAsyncFn} from 'modules/testUtils';

import Selections from './Selections';

import * as api from 'modules/api/selections/selections';

api.batchRetry = mockResolvedAsyncFn();

const MockSelections = [
  {
    queries: [
      {
        active: true,
        excludeIds: [],
        ids: [],
        incidents: true,
        running: true
      }
    ],
    selectionId: 0,
    totalCount: 3,
    workflowInstances: [{id: 1}, {id: 2}, {id: 3}]
  },
  {
    queries: [
      {
        active: true,
        excludeIds: [],
        ids: [],
        incidents: false,
        running: true
      }
    ],
    selectionId: 1,
    totalCount: 3,
    workflowInstances: [{id: 1}, {id: 2}, {id: 3}]
  }
];

describe('Selections', () => {
  let node;
  let mockHandleStateChange;
  let storeStateLocally;

  beforeEach(() => {
    mockHandleStateChange = jest.fn();
    storeStateLocally = jest.fn();

    node = shallow(
      <Selections
        openSelection={0}
        selections={MockSelections}
        selectionCount={2}
        instancesInSelectionsCount={6}
        handleStateChange={mockHandleStateChange}
        storeStateLocally={storeStateLocally}
      />
    );
  });

  it('should match this snapshot', () => {
    expect(node).toMatchSnapshot();
  });

  it('should close a selection', () => {
    //when
    node.instance().toggleSelection(0);

    //then
    expect(node.instance().props.handleStateChange).toBeCalledWith({
      openSelection: null
    });
  });

  it('should open a selection when non is open', () => {
    // given
    node.setProps({openSelection: null});

    // when
    node.instance().toggleSelection(0);

    // then
    expect(node.instance().props.handleStateChange).toBeCalledWith({
      openSelection: 0
    });
  });

  it('should only show one open selection', () => {
    // given
    node.setProps({openSelection: null});

    //when
    node.instance().toggleSelection(0);
    // then
    expect(node.instance().props.handleStateChange).toBeCalledWith({
      openSelection: 0
    });
  });

  it('should delete a selection', () => {
    //when
    node.instance().deleteSelection(0);

    //then
    expect(node.instance().props.handleStateChange).toBeCalledWith({
      selections: MockSelections,
      instancesInSelectionsCount: 3,
      selectionCount: 1
    });
  });

  it('retry instances in a selection', () => {
    //when
    node.instance().retrySelection(0);

    //then
    expect(api.batchRetry).toBeCalled();
  });
});
