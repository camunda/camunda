/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {mount, shallow} from 'enzyme';

import Popover from './Popover';

it('should do not display child content initially', () => {
  const node = shallow(<Popover trigger={<Popover.Button />}>Child content</Popover>);

  expect(node).not.toIncludeText('Child content');
});

it('should automatically open the popover on mount with the autoOpen prop', () => {
  const node = shallow(
    <Popover trigger={<Popover.Button />} autoOpen>
      Child content
    </Popover>
  );

  expect(node).toIncludeText('Child content');
});

it('should open/close popover based on listbox trigger', () => {
  const node = mount(
    <Popover trigger={<Popover.ListBox label="test">test</Popover.ListBox>}>Child content</Popover>
  );

  expect(node.find('.popoverContent').exists()).toBe(false);

  node.find('ListBox button').simulate('click');

  expect(node.find('.popoverContent').exists()).toBe(true);
});

it('should open/close popover based on button trigger', () => {
  const node = mount(
    <Popover trigger={<Popover.Button>test</Popover.Button>}>Child content</Popover>
  );

  expect(node.find('.popoverContent').exists()).toBe(false);

  node.find('ButtonTrigger button').simulate('click');

  expect(node.find('.popoverContent').exists()).toBe(true);
});
