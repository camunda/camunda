import React from 'react';
import {shallow} from 'enzyme';

import StateIcon from 'modules/components/StateIcon';

import Dropdown from 'modules/components/Dropdown';
import Selection from './Selection';
import * as Styled from './styled';
// import {debug} from 'util';

const demoInstance = {
  id: '4294984040',
  workflowId: '1',
  startDate: '2018-07-10T08:58:58.073+0000',
  endDate: null,
  state: 'ACTIVE',
  bpmnProcessId: 'demoProcess',
  incidents: [],
  activities: []
};

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
        selectionId={0}
        instances={[demoInstance]}
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
    expect(node.find(Styled.Header).props().isOpen).toBe(true);
  });

  it('should contain Instances', () => {
    expect(node.find(Styled.Instance)).toExist();
    expect(node.find(StateIcon)).toExist();
    expect(node.find(Styled.WorkflowName)).toExist();
    expect(node.find(Styled.InstanceId)).toExist();
  });

  it('should contain a Footer', () => {
    expect(node.find(Styled.Footer)).toExist();
    expect(node.find(Styled.MoreInstances)).toExist();
    expect(node.find(Styled.MoreInstances).contains('144 more Instances')).toBe(
      true
    );
  });

  it('should contain Actions', () => {
    expect(node.find(Styled.Actions)).toExist();
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
    node.find('[data-test="retry-dropdown-option"]').simulate('click');
    expect(mockOnRetry).toHaveBeenCalled();
  });

  it('should call the passed cancel method', () => {
    node.find(Styled.DropdownTrigger).simulate('click');
    node.find('[data-test="cancel-dropdown-option"]').simulate('click');
    expect(mockOnCancel).toHaveBeenCalled();
  });
});
