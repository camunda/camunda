import React from 'react';
import {shallow} from 'enzyme';

import SubOption from './SubOption';

const mockOnClick = jest.fn();
const mockOnStateChange = jest.fn();

describe('SubOption', () => {
  let node;

  beforeEach(() => {
    node = shallow(
      <SubOption onClick={mockOnClick} onStateChange={mockOnStateChange}>
        <span>I'am a child</span>
      </SubOption>
    );
  });

  it('should render its children', () => {
    expect(node.find('span')).toExist();
  });

  it('should handle the button click', () => {
    node.simulate('click');
    expect(mockOnClick).toHaveBeenCalled();
  });

  it('should close the dropdown on click', () => {
    node.simulate('click');
    expect(mockOnStateChange).toHaveBeenCalledWith({isOpen: false});
  });
});
