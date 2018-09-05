import React from 'react';
import {shallow} from 'enzyme';

import {mockResolvedAsyncFn, flushPromises} from 'modules/testUtils';

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
  let mockonStateChange;
  let storeStateLocally;

  beforeEach(() => {
    mockonStateChange = jest.fn();
    storeStateLocally = jest.fn();

    node = shallow(
      <Selections
        openSelection={0}
        selections={MockSelections}
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

  it('should close a selection', () => {
    //when
    node.instance().handleToggleSelection(0);

    //then
    expect(node.instance().props.onStateChange).toBeCalledWith({
      openSelection: null
    });
  });

  it('should open a selection when non is open', () => {
    // given
    node.setProps({openSelection: null});

    // when
    node.instance().handleToggleSelection(0);

    // then
    expect(node.instance().props.onStateChange).toBeCalledWith({
      openSelection: 0
    });
  });

  it('should only show one open selection', () => {
    // given
    node.setProps({openSelection: null});

    //when
    node.instance().handleToggleSelection(0);
    // then
    expect(node.instance().props.onStateChange).toBeCalledWith({
      openSelection: 0
    });
  });

  it('should delete a selection', () => {
    //when
    node.instance().handleDeleteSelection(0);

    //then
    expect(node.instance().props.onStateChange).toBeCalledWith({
      selections: MockSelections,
      instancesInSelectionsCount: 3,
      selectionCount: 1
    });
  });

  it('retry instances in a selection', async () => {
    //when
    node.instance().handleRetrySelection(1);

    // when data fetched
    await flushPromises();

    //then
    expect(api.batchRetry).toHaveBeenCalledWith(MockSelections[0].queries);
  });
});
