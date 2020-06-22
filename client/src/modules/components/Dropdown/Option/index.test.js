/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import Option from './index';
import Dropdown from '../index';

import * as Styled from './styled';

describe('Option', () => {
  let node;
  let Child;
  let onClickMock;
  let mockOnStateChange;

  beforeEach(() => {
    Child = () => <span>I am a label</span>;
    onClickMock = jest.fn();
    mockOnStateChange = jest.fn();

    node = shallow(
      <Option onClick={onClickMock}>
        <Child />
      </Option>
    );
  });

  it('should render a button if no children are passed', () => {
    node = shallow(<Option onClick={onClickMock} />);
    expect(node.find(Styled.OptionButton)).toExist();
  });

  it('should render passed children', () => {
    expect(node.find(Child)).toExist();
  });

  it('should render child with props', () => {
    node = shallow(
      <Option
        isSubMenuOpen={true}
        isSubmenuFixed={false}
        onStateChange={mockOnStateChange}
        onClick={onClickMock}
      >
        <Dropdown.SubMenu onStateChange={mockOnStateChange}>
          <Dropdown.SubOption>'foo'</Dropdown.SubOption>
        </Dropdown.SubMenu>
      </Option>
    );

    const SubMenuProps = node.find(Dropdown.SubMenu).props();

    expect(SubMenuProps.isOpen).toBe(true);
    expect(SubMenuProps.isFixed).toBe(false);
  });

  it('should handle click event', () => {
    node = shallow(
      <Option onClick={onClickMock} onStateChange={mockOnStateChange} />
    );

    const clickSpy = jest.spyOn(node.instance(), 'handleOnClick');
    node.setProps({disabled: false});
    node.find(Styled.Option).simulate('click');

    expect(clickSpy).toHaveBeenCalled();
    expect(onClickMock).toHaveBeenCalled();
    expect(mockOnStateChange).toHaveBeenCalledWith({isOpen: false});
  });
});
