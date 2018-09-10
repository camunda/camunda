import React from 'react';
import {shallow} from 'enzyme';

import ListFooter from './ListFooter';
import Paginator from './Paginator';

import Dropdown from 'modules/components/Dropdown';
import ContextualMessage from 'modules/components/ContextualMessage';
import {DROPDOWN_PLACEMENT, CONTEXTUAL_MESSAGE_TYPE} from 'modules/constants';

import {xTimes, createSelection} from 'modules/testUtils';

import * as Styled from './styled.js';

const maxSelections = [];

xTimes(10)(index => maxSelections.push(createSelection(index)));

describe('ListFooter', () => {
  let node;
  let onAddNewSelectionSpy,
    onAddToOpenSelectionSpy,
    onAddToSpecificSelectionSpy;

  beforeEach(() => {
    node = shallow(
      <ListFooter
        onFirstElementChange={jest.fn()}
        onAddNewSelection={jest.fn()}
        onAddToSpecificSelection={jest.fn()}
        onAddToOpenSelection={jest.fn()}
        perPage={10}
        firstElement={0}
        total={9}
        selection={{ids: new Set(), excludeIds: new Set()}}
        selections={[{selectionId: 0}, {selectionId: 1}]}
      />
    );
    onAddNewSelectionSpy = jest.spyOn(
      node.instance().props,
      'onAddNewSelection'
    );
    onAddToOpenSelectionSpy = jest.spyOn(
      node.instance().props,
      'onAddToOpenSelection'
    );
    onAddToSpecificSelectionSpy = jest.spyOn(
      node.instance().props,
      'onAddToSpecificSelection'
    );
  });

  it('should pagination only if required', () => {
    expect(node.find(Paginator).exists()).toBe(false);
    node.setProps({total: 11});
    expect(node.find(Paginator).exists()).toBe(true);
  });

  it('should render button if no selection exists', () => {
    node.setProps({selections: []});
    expect(node.find(Styled.SelectionButton).exists()).toBe(true);
  });

  it('should render dropdown if selection exists', () => {
    expect(node.find(Dropdown).exists()).toBe(true);
  });

  describe('DropdownMenu', () => {
    it('should drop "up"', () => {
      expect(node.find(Dropdown).props().placement).toBe(
        DROPDOWN_PLACEMENT.TOP
      );
    });

    it('should add new selection', () => {
      const createSelectionOption = node.find('Dropdown').childAt(0);
      createSelectionOption.simulate('click');
      expect(onAddNewSelectionSpy).toHaveBeenCalled();
    });

    it('should add instances to open selection', () => {
      node.setProps({openSelection: 1});
      const addToOpenSelectionOption = node.find('Dropdown').childAt(1);
      addToOpenSelectionOption.simulate('click');
      expect(onAddToOpenSelectionSpy).toHaveBeenCalled();
    });

    it('should disable "add to open selection" option if no selection open', () => {
      const addToOpenSelectionOption = node.find('Dropdown').childAt(1);
      expect(addToOpenSelectionOption.props().disabled).toBe(true);
    });

    it('should show contextual message when max number of Selections is reached', () => {
      //when
      node.setProps({
        selections: maxSelections
      });

      //then
      const createSelectionOption = node.find('Dropdown').childAt(0);
      expect(createSelectionOption.props().disabled).toBe(true);

      const ContextualMessageComponent = node.find(ContextualMessage);
      expect(ContextualMessageComponent.exists()).toBe(true);
      expect(ContextualMessageComponent.props().type).toBe(
        CONTEXTUAL_MESSAGE_TYPE.DROP_SELECTION
      );
    });
  });

  describe('SubDropdownMenu', () => {
    it('should show all selection ids', () => {
      const noOfSelections = node.instance().props.selections.length;
      expect(node.find('SubMenu').children().length).toBe(noOfSelections);
    });

    it('should add instances to specific selection', () => {
      const onAddToSpecificSelectionOption = node.find('SubMenu').childAt(0);
      onAddToSpecificSelectionOption.simulate('click');
      expect(onAddToSpecificSelectionSpy).toHaveBeenCalledWith(0);
    });
  });
});
