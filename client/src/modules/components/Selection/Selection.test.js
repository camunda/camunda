import React from 'react';
import {shallow} from 'enzyme';

import {createInstance} from 'modules/testUtils';

import StateIcon from 'modules/components/StateIcon';

import Dropdown from 'modules/components/Dropdown';
import Selection from './Selection';
import * as Styled from './styled';

const mockInstance = createInstance();
const mockMap = new Map([['1', mockInstance]]);

const mockOnClick = jest.fn();
const mockOnRetry = jest.fn();
const mockOnCancel = jest.fn();
const mockOnDelete = jest.fn();

describe('Selection', () => {
  let node;
  const isOpen = true;
  beforeEach(() => {
    node = shallow(
      <Selection
        isOpen={isOpen}
        selectionId={1}
        instances={mockMap}
        instanceCount={145}
        onToggle={mockOnClick}
        onRetry={mockOnRetry}
        onCancel={mockOnCancel}
        onDelete={mockOnDelete}
      />
    );
  });

  it('should contain a Header', () => {
    expect(node.find(Styled.Dt)).toExist();
    expect(node.find(Styled.Dt)).toMatchSnapshot();
    expect(node.find(Styled.Dt).props().isOpen).toBe(true);
  });

  it('should contain Instances', () => {
    expect(node.find('ul')).toExist();
    expect(node.find('ul')).toMatchSnapshot();

    expect(node.find(Styled.StatusCell)).toExist();
    expect(node.find(StateIcon)).toExist();
    expect(node.find(Styled.NameCell)).toExist();
    expect(node.find(Styled.IdCell)).toExist();
    expect(node.find(Styled.ActionStatusCell)).toExist();
    expect(node.find(Styled.InstanceActionStatus)).toExist();
  });

  it('should contain a Footer', () => {
    expect(node.find(Styled.Footer)).toExist();
    expect(node.find(Styled.Footer)).toMatchSnapshot();
    expect(node.find(Styled.MoreInstances)).toExist();
    expect(node.find(Styled.MoreInstances).contains('144 more Instances')).toBe(
      true
    );
  });

  it('should contain Actions', () => {
    expect(node.find(Styled.Actions)).toExist();
    expect(node.find(Styled.Actions)).toMatchSnapshot();
    expect(node.find(Styled.DropdownWrapper)).toExist();
    expect(node.find(Dropdown)).toExist();
    expect(node.find(Styled.DeleteIcon)).toExist();
    expect(node.find(Styled.ActionButton).props().onClick).toBe(mockOnDelete);
  });

  it('should only have Header when closed', () => {
    //given
    node.setProps({isOpen: false});

    // when
    node.update();

    // then
    expect(node.find(Styled.SelectionToggle)).toExist();

    expect(node.find(Styled.Dd)).not.toExist();
    expect(node.find(Styled.Footer)).not.toExist();
    expect(node.find(Styled.Actions)).not.toExist();
  });

  it('should call the passed toggle method', () => {
    node.find(Styled.SelectionToggle).simulate('click');
    expect(mockOnClick).toHaveBeenCalled();
  });

  it('should call the passed delete method', () => {
    node.find(Styled.ActionButton).simulate('click');
    expect(mockOnDelete).toHaveBeenCalled();
  });

  it('should call the passed retry method', () => {
    node.find(Dropdown).simulate('click');
    node.find('[data-test="UPDATE_RETRIES-dropdown-option"]').simulate('click');
    expect(mockOnRetry).toHaveBeenCalled();
  });

  it('should call the passed cancel method', () => {
    node.find(Dropdown).simulate('click');
    node.find('[data-test="CANCEL-dropdown-option"]').simulate('click');
    expect(mockOnCancel).toHaveBeenCalled();
  });

  describe('newInstances', () => {
    let newMockInstance = createInstance();
    let newMockMap;

    beforeEach(() => {
      newMockMap = new Map([['1', mockInstance], ['2', newMockInstance]]);
      node.setProps({instances: newMockMap});
      node.update();
    });

    it('should store a count of instances which are subsequently added', () => {
      expect(node.instance().state.numberOfNewInstances).toBe(1);
    });

    it('should reset the newInstances count when closed ', () => {
      //given
      node.setProps({isOpen: false});

      // when
      node.update();

      // then
      expect(node.instance().state.numberOfNewInstances).toBe(0);
    });

    it("should tell the instance when it's new", () => {
      expect(
        node
          .find('ul')
          .childAt(0)
          .props().isNew
      ).toBe(true);

      expect(
        node
          .find('ul')
          .childAt(1)
          .props().isNew
      ).toBe(false);
    });
  });
});
