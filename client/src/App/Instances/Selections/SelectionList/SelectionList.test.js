import React from 'react';
import {shallow} from 'enzyme';

import SelectionList from './SelectionList';
import {NO_SELECTIONS_MESSAGE} from './constants';

import * as Styled from './styled';

describe('SelectionList', () => {
  let node;
  let selections;

  beforeEach(async () => {
    selections = [
      {
        queries: [],
        selectionId: 1,
        totalCount: 1,
        workflowInstances: []
      }
    ];
    node = shallow(
      <SelectionList
        selections={selections}
        deleteSelection={jest.fn()}
        toggleSelection={jest.fn()}
        retrySelection={jest.fn()}
      />
    );
  });

  it('should render "no selection text" when no selection exists', () => {
    //when
    selections = [];
    node.setProps({selections});

    // then
    const NoSelectionWrapper = node.find(Styled.NoSelectionWrapper);
    expect(NoSelectionWrapper).toHaveLength(1);
    expect(NoSelectionWrapper.contains(NO_SELECTIONS_MESSAGE)).toBe(true);
    expect(node).toMatchSnapshot();
  });

  it('should render Selection when existent', () => {
    expect(node.find(Styled.SelectionWrapper));
  });

  it('should evaluate which Selection is currently open', () => {
    // given
    node.setProps({openSelection: 1});

    //then
    expect(node.find('Selection').props().isOpen).toBe(true);
  });
});
