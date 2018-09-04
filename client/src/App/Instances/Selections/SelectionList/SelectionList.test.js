import React from 'react';
import {shallow} from 'enzyme';

import SelectionList from './SelectionList';
import ContextualMessage from 'modules/components/ContextualMessage';

import {NO_SELECTIONS_MESSAGE} from './constants';

import {xTimes, createSelection} from 'modules/testUtils';

import * as Styled from './styled';

describe('SelectionList', () => {
  let node;
  let selections;

  beforeEach(async () => {
    selections = [];

    xTimes(1)(index => selections.push(createSelection(index)));

    node = shallow(
      <SelectionList
        selections={selections}
        onDeleteSelection={jest.fn()}
        onToggleSelection={jest.fn()}
        onRetrySelection={jest.fn()}
      />
    );
  });

  it('should render a message when no selection exists', () => {
    //when
    selections = [];
    node.setProps({selections});

    // then
    const NoSelectionWrapper = node.find(Styled.NoSelectionWrapper);
    expect(NoSelectionWrapper).toHaveLength(1);
    expect(NoSelectionWrapper.contains(NO_SELECTIONS_MESSAGE)).toBe(true);
    expect(node).toMatchSnapshot();
  });

  it('should render contexual message when max. number of selections is reached', () => {
    // given
    selections = [];
    expect(node.find(ContextualMessage)).not.toExist();

    // when
    xTimes(10)(index => selections.push(createSelection(index)));
    node.setProps({selections});

    // then
    expect(node.find(ContextualMessage)).toExist();
  });

  it('should render Selection when existent', () => {
    expect(node.find(Styled.MessageWrapper));
  });

  it('should evaluate which Selection is currently open', () => {
    // when
    node.setProps({openSelection: 1});

    // then
    expect(node.find('Selection').props().isOpen).toBe(true);
  });

  it('should pass props to the Selection component', () => {
    expect(node.find('Selection').props()).toMatchSnapshot();
  });
});
