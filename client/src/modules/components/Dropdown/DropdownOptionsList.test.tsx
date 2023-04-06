/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {MouseEventHandler} from 'react';
import {shallow} from 'enzyme';
import {ignoreFragments} from 'services';

import DropdownOptionsList from './DropdownOptionsList';
import Dropdown from './Dropdown';
import {MouseEvent} from 'react';

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  ignoreFragments: jest.fn().mockImplementation((children) => children),
}));

jest.useFakeTimers();

beforeEach(() => {
  jest.clearAllMocks();
});

it('should open and close a submenu', () => {
  const node = shallow(
    <DropdownOptionsList>
      <Dropdown.Submenu />
      <Dropdown.Submenu />
    </DropdownOptionsList>
  );

  node.find(Dropdown.Submenu).at(0).prop('setOpened')();

  expect(node.find(Dropdown.Submenu).at(0)).toHaveProp('open', true);

  node.find(Dropdown.Submenu).at(0).prop('setClosed')();
  jest.runAllTimers();

  expect(node.find(Dropdown.Submenu).at(0)).toHaveProp('open', false);
});

it('should not open a submenu if there is a fixed submenu opened', () => {
  const node = shallow(
    <DropdownOptionsList>
      <Dropdown.Submenu />
      <Dropdown.Submenu />
    </DropdownOptionsList>
  );

  node.find(Dropdown.Submenu).at(1).prop('forceToggle')({
    stopPropagation: jest.fn(),
    preventDefault: jest.fn(),
  });
  node.find(Dropdown.Submenu).at(0).prop('setOpened')();

  expect(node.find(Dropdown.Submenu).at(0)).toHaveProp('open', false);
  expect(node.find(Dropdown.Submenu).at(1)).toHaveProp('open', true);
});

it('should invoke close parent if called from a submenu item', () => {
  const spy = jest.fn();
  const node = shallow(
    <DropdownOptionsList closeParent={spy}>
      <Dropdown.Submenu />
    </DropdownOptionsList>
  );

  node.find(Dropdown.Submenu).prop('closeParent')();

  expect(spy).toHaveBeenCalled();
});

it('should reset scheduled removal of the submenu when entering the menu', async () => {
  const node = shallow(
    <DropdownOptionsList>
      <Dropdown.Submenu />
    </DropdownOptionsList>
  );

  node.find(Dropdown.Submenu).prop('setOpened')();
  node.find(Dropdown.Submenu).prop('setClosed')();
  node.find(Dropdown.Submenu).prop('onMenuMouseEnter')();
  jest.runAllTimers();

  expect(node.find(Dropdown.Submenu)).toHaveProp('open', true);
});

it('should close the submenu immediately after leaving the menu', async () => {
  const node = shallow(
    <DropdownOptionsList>
      <Dropdown.Submenu />
    </DropdownOptionsList>
  );

  node.find(Dropdown.Submenu).prop('setOpened')();
  node.find(Dropdown.Submenu).prop('onMenuMouseLeave')();

  expect(node.find(Dropdown.Submenu)).toHaveProp('open', false);
});

it('should close the submenu immediately when hovering over another option', async () => {
  const node = shallow(
    <DropdownOptionsList>
      <Dropdown.Submenu />
      <Dropdown.Option />
    </DropdownOptionsList>
  );

  node.find(Dropdown.Submenu).prop('setOpened')();
  node.find(Dropdown.Option).prop<MouseEventHandler<HTMLDivElement>>('onMouseEnter')?.({
    target: document.createElement('div'),
  } as unknown as MouseEvent<HTMLDivElement>);

  expect(node.find(Dropdown.Submenu)).toHaveProp('open', false);
});

it('should invoke ignoreFragments when rendering the list', async () => {
  const children = [<Dropdown.Submenu key="1" />, <Dropdown.Option key="2" />];
  shallow(<DropdownOptionsList>{children}</DropdownOptionsList>);

  expect(ignoreFragments).toHaveBeenCalledWith(children);
});
