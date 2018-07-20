import React from 'react';
import {shallow} from 'enzyme';

import Checkbox from './Checkbox';

import * as Styled from './styled';

describe('<Checkbox />', () => {
  const mockOnChange = jest.fn();

  it('should update "isChecked" state according to props', () => {
    // given
    let isChecked = true;
    const node = shallow(
      <Checkbox onChange={mockOnChange} isChecked={isChecked} />
    );
    expect(node.state().isChecked).toBe(true);

    // when
    isChecked = false;
    node.setProps({isChecked});

    // then
    expect(node.state().isChecked).toBe(false);
  });

  it('should toggle "isChecked" prop on click', () => {
    let checkState = false;
    const mockOnChange = jest
      .fn()
      .mockImplementation((event, isChecked) => (checkState = isChecked));
    const node = shallow(
      <Checkbox onChange={mockOnChange} isChecked={checkState} />
    );

    expect(checkState).toBe(false);
    node.find(Styled.Input).simulate('change', {target: {checked: true}});
    expect(checkState).toBe(true);
  });

  it('should display a label if passed as props', () => {
    const node = shallow(
      <Checkbox label={'foo'} onChange={mockOnChange} isChecked={true} />
    );

    expect(node.find(Styled.Label)).toExist();
  });

  it('should pass the value of the Checkbox to the onChange method', () => {
    let checkState = true;
    const node = shallow(
      <Checkbox onChange={mockOnChange} isChecked={checkState} />
    );

    const event = {target: {checked: true}};

    node.instance().inputRef({checked: true});
    node.instance().handleOnChange(event);

    expect(mockOnChange).toBeCalledWith(event, true);
  });
});
