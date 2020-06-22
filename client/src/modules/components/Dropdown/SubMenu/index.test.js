/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import SubMenu from './index';

import * as Styled from './styled';

describe('SubMenu', () => {
  let node;
  beforeEach(() => {
    node = shallow(
      <SubMenu isOpen={false} isFixed={false} onStateChange={jest.fn()}>
        <span>I am a Dropdown.SubOption Component</span>
      </SubMenu>
    );
  });

  it('should render children when open', () => {
    //given
    expect(node.find(Styled.Li)).not.toExist();

    //when
    node.setProps({isOpen: true});

    //then
    expect(node.find(Styled.Li)).toExist();
  });

  it('should render a button', () => {
    expect(node.find(Styled.SubMenuButton)).toExist();
  });

  it('should open submenu on mouse over', () => {
    node.setProps({isOpen: false, isFixed: false});

    node.instance().handleButtonMouseOver();

    expect(node.instance().props.onStateChange).toHaveBeenCalledWith({
      isSubMenuOpen: true,
    });
  });

  it('should close submenu on mouse leave', () => {
    //given
    node.setProps({isOpen: true, isFixed: false});

    //when
    node.instance().handleMouseLeave();

    //then
    expect(node.instance().props.onStateChange).toHaveBeenCalledWith({
      isSubMenuOpen: false,
    });
  });

  it('should fix the open submenu when clicked', async () => {
    //Given
    node.setProps({isOpen: true, isFixed: false});

    //When
    node.instance().handleOnClick();

    //Then
    expect(node.instance().props.onStateChange).toHaveBeenCalledWith({
      isSubmenuFixed: true,
    });
  });

  it('should close and unfix the submenu when clicked', async () => {
    //Given
    node.setProps({isOpen: true, isFixed: true});

    //When
    node.instance().handleOnClick();

    //Then
    expect(node.instance().props.onStateChange).toHaveBeenCalledWith({
      isSubMenuOpen: false,
      isSubmenuFixed: false,
    });
  });

  it('should open and fix the submenu after being closed via onclick', async () => {
    // Given
    node.setProps({isOpen: false, isFixed: false});

    // When
    node.instance().handleOnClick();

    // Then
    expect(node.instance().props.onStateChange).toHaveBeenCalledWith({
      isSubMenuOpen: true,
      isSubmenuFixed: true,
    });
  });
});
