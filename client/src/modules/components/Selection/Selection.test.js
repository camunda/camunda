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
    expect(node.find(Styled.Header)).toExist();
    expect(node.find(Styled.Header)).toMatchSnapshot();
    expect(node.find(Styled.Header).props().isOpen).toBe(true);
  });

  it('should contain Instances', () => {
    expect(node.find(Styled.Instance)).toExist();
    expect(node.find(Styled.Instance)).toMatchSnapshot();
    expect(node.find(StateIcon)).toExist();
    expect(node.find(Styled.WorkflowName)).toExist();
    expect(node.find(Styled.InstanceId)).toExist();
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
    expect(node.find(Styled.DropdownTrigger)).toExist();
    expect(node.find(Dropdown)).toExist();
    expect(node.find(Styled.DeleteIcon)).toExist();
    expect(node.find(Styled.DeleteIcon).props().onClick).toBe(mockOnDelete);
  });

  it('should only have Header when closed', () => {
    //given
    node.setProps({isOpen: false});

    // when
    node.update();

    // then
    expect(node.find(Styled.Header)).toExist();

    expect(node.find(Styled.Instance)).not.toExist();
    expect(node.find(Styled.Footer)).not.toExist();
    expect(node.find(Styled.Actions)).not.toExist();
  });

  it('should call the passed toggle method', () => {
    node.find(Styled.Header).simulate('click');
    expect(mockOnClick).toHaveBeenCalled();
  });

  it('should call the passed delete method', () => {
    node.find(Styled.DeleteIcon).simulate('click');
    expect(mockOnDelete).toHaveBeenCalled();
  });

  it('should call the passed retry method', () => {
    node.find(Styled.DropdownTrigger).simulate('click');
    node.find('[data-test="UPDATE_RETRIES-dropdown-option"]').simulate('click');
    expect(mockOnRetry).toHaveBeenCalled();
  });

  it('should call the passed cancel method', () => {
    node.find(Styled.DropdownTrigger).simulate('click');
    node.find('[data-test="CANCEL-dropdown-option"]').simulate('click');
    expect(mockOnCancel).toHaveBeenCalled();
  });
});
