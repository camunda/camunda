import React from 'react';
import {shallow} from 'enzyme';

import AddSelection from './AddSelection';
import Dropdown from 'modules/components/Dropdown';
import ContextualMessage from 'modules/components/ContextualMessage';
import {DROPDOWN_PLACEMENT, CONTEXTUAL_MESSAGE_TYPE} from 'modules/constants';

import {xTimes, createSelection} from 'modules/testUtils';

const maxSelections = [];
xTimes(10)(index => maxSelections.push(createSelection(index)));

describe('AddSelection', () => {
  let node;
  let onAddNewSelectionSpy,
    onAddToOpenSelectionSpy,
    onAddToSpecificSelectionSpy,
    toggleSelectionsSpy;

  beforeEach(() => {
    node = shallow(
      <AddSelection
        onAddNewSelection={jest.fn()}
        onAddToSpecificSelection={jest.fn()}
        onAddToOpenSelection={jest.fn()}
        selection={{ids: [], excludeIds: []}}
        selections={[{selectionId: 0}, {selectionId: 1}]}
        isSelectionsCollapsed={true}
        toggleSelections={jest.fn()}
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
    toggleSelectionsSpy = jest.spyOn(node.instance().props, 'toggleSelections');
  });

  afterEach(() => {
    onAddNewSelectionSpy.mockRestore();
    onAddToOpenSelectionSpy.mockRestore();
    onAddToSpecificSelectionSpy.mockRestore();
    toggleSelectionsSpy.mockRestore();
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
      expect(toggleSelectionsSpy).toHaveBeenCalled();
    });

    it('should add instances to open selection', () => {
      node.setProps({openSelection: 1});
      const addToOpenSelectionOption = node.find('Dropdown').childAt(1);
      addToOpenSelectionOption.simulate('click');
      expect(onAddToOpenSelectionSpy).toHaveBeenCalled();
    });

    it('should open the selections panel on dropdown click', () => {
      node.setProps({openSelection: 1});
      const dropdownNode = node.find('Dropdown');
      expect(dropdownNode.props().onOpen).toEqual(
        node.instance().openSelectionsPanel
      );
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
      // given
      const onAddToSpecificSelectionOption = node.find('SubMenu').childAt(0);

      // when
      onAddToSpecificSelectionOption.simulate('click');

      // then
      expect(onAddToSpecificSelectionSpy).toHaveBeenCalledWith(0);
    });
  });
});
