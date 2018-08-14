import React from 'react';
import {shallow} from 'enzyme';

import Dropdown from 'modules/components/Dropdown';
import Selection from './Selection';
import * as Styled from './styled';

const demoInstance = {
  id: '4294984040',
  workflowId: '1',
  startDate: '2018-07-10T08:58:58.073+0000',
  endDate: null,
  state: 'ACTIVE',
  businessKey: 'demoProcess',
  incidents: [],
  activities: []
};

const mockOnClick = jest.fn();
const mockOnRetry = jest.fn();
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
        onDelete={mockOnDelete}
      />
    );
  });

  it('should contain a Header', () => {
    expect(node.find(Styled.Header)).toExist();
  });

  it('should contain Instances', () => {
    expect(node.find(Styled.Instance)).toExist();
  });

  it('should contain a Footer', () => {
    expect(node.find(Styled.Footer)).toExist();
  });

  it('should contain Action Icons', () => {
    expect(node.find(Styled.Actions)).toExist();
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
    node.find(Dropdown.Option).simulate('click');
    expect(mockOnRetry).toHaveBeenCalled();
  });
});
