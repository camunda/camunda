import React from 'react';
import {shallow} from 'enzyme';

import {PANE_STATE} from 'modules/components/SplitPane/Pane/constants';

import List from './List';

describe('List', () => {
  const mockProps = {
    data: [{}],
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
});
