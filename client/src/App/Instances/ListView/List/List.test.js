import React from 'react';
import {shallow} from 'enzyme';

import {PANE_STATE} from 'modules/components/SplitPane/Pane/constants';

import List from './List';

const instance = {
  activities: [],
  businessKey: 'orderProcess',
  endDate: null,
  id: '4294968496',
  incidents: [],
  startDate: '2018-06-21T11:13:31.094+0000',
  state: 'ACTIVE',
  workflowId: '2'
};

describe('List', () => {
  const mockProps = {
    data: [instance],
    onSelectionUpdate: jest.fn(),
    onEntriesPerPageChange: jest.fn(),
    selection: {
      exclusionList: new Set(),
      query: {},
      list: [{}]
    },
    paneState: PANE_STATE.DEFAULT
  };

  it('should have by default rowsToDisplay null', () => {
    expect(new List().state.rowsToDisplay).toBe(null);
  });

  it('should call recalculateHeight on mount', () => {
    // given
    const recalculateHeightSpy = jest.spyOn(
      List.prototype,
      'recalculateHeight'
    );
    shallow(<List {...mockProps} />);

    // then
    expect(recalculateHeightSpy).toBeCalled();
  });

  it('should only call recalculateHeight when needed', () => {
    // given
    const recalculateHeightSpy = jest.spyOn(
      List.prototype,
      'recalculateHeight'
    );
    const node = shallow(<List {...mockProps} />);
    recalculateHeightSpy.mockClear();

    // when component updates but paneState does not change
    node.setProps({paneState: PANE_STATE.DEFAULT});
    // then recalculateHeight should not be called
    expect(recalculateHeightSpy).not.toBeCalled();

    // when component updates but paneState is COLLAPSED
    node.setProps({paneState: PANE_STATE.COLLAPSED});
    // then recalculateHeight should not be called
    expect(recalculateHeightSpy).not.toBeCalled();

    // when component updates and paneState changed and is not COLLAPSED
    node.setProps({paneState: PANE_STATE.EXPANDED});
    // then recalculateHeight should not be called
    expect(recalculateHeightSpy).toBeCalled();
  });

  it.only('renders a table row with a link to the instance page', () => {
    const node = shallow(<List {...mockProps} />);
    const formatData = node.instance().formatData;
    const rowData = formatData(instance);

    expect(rowData.id.props.to).toBe(`/instances/${instance.id}`);
    expect(rowData.id.props.children).toBe(instance.id);
  });
});
