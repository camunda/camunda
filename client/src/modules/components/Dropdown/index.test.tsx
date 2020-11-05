/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

import {ReactComponent as Batch} from 'modules/components/Icon/batch.svg';
import {DROPDOWN_PLACEMENT} from 'modules/constants';

import Dropdown from './index';

import * as Styled from './styled';

const buttonStyles = {
  fontSize: '13px',
};

const stringLabel = 'Some Label';

const mockOnOpen = jest.fn();
const mockOnClick = jest.fn();

const mountDropDown = (placement: any) => {
  return mount(
    <ThemeProvider>
      <Dropdown
        placement={placement}
        label={stringLabel}
        // @ts-expect-error ts-migrate(2769) FIXME: Property 'buttonStyle' does not exist on type 'Int... Remove this comment to see the full error message
        buttonStyle={buttonStyles}
        onOpen={mockOnOpen}
      >
        <Dropdown.Option
          disabled={false}
          onClick={mockOnClick}
          label="Create New Selection"
        />
      </Dropdown>
    </ThemeProvider>
  );
};

describe('Dropdown', () => {
  let node: any;

  beforeEach(() => {
    node = mountDropDown(DROPDOWN_PLACEMENT.TOP);
    mockOnOpen.mockClear();
  });

  it('should show/hide child contents when isOpen/closed', () => {
    //given
    expect(node.find(Dropdown.Option)).not.toExist();

    //when
    node
      .find('button')
      .find("[data-testid='dropdown-toggle']")
      .simulate('click');

    //then
    expect(node.find(Dropdown.Option)).toExist();
  });

  it('should render string label', () => {
    const label = node.find(Styled.LabelWrapper);
    expect(label.contains(stringLabel));
  });

  it('should render icon label', () => {
    node.setProps({label: <Batch />});
    expect(node.find(Dropdown.Option).contains(<Batch />));
  });

  it('should pass "bottom" as default placement', () => {
    // @ts-expect-error ts-migrate(2554) FIXME: Expected 1 arguments, but got 0.
    node = mountDropDown();
    expect(node.find(Dropdown).instance().props.placement).toBe(
      DROPDOWN_PLACEMENT.BOTTOM
    );
  });

  it('should isOpen/close on click of the button', () => {
    //given
    node
      .find('button')
      .find("[data-testid='dropdown-toggle']")
      .simulate('click');
    expect(node.find(Dropdown).state().isOpen).toBe(true);
    //when
    node
      .find('button')
      .find("[data-testid='dropdown-toggle']")
      .simulate('click');
    //then
    expect(node.find(Dropdown).state().isOpen).toBe(false);
  });

  it('should close the dropdown when clicking anywhere', async () => {
    //given
    const onCloseSpy = jest.spyOn(node.find(Dropdown).instance(), 'onClose');
    await node.find(Dropdown).instance().componentDidMount();
    //when
    document.body.click();
    //then
    expect(onCloseSpy).toHaveBeenCalled();
  });

  it('should call onOpen on the initial click', () => {
    // when clicking to open
    node
      .find('button')
      .find("[data-testid='dropdown-toggle']")
      .simulate('click');
    expect(mockOnOpen).toHaveBeenCalledTimes(1);
  });
});
