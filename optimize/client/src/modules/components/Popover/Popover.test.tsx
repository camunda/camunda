/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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

  node.find(Popover.ListBox).find('button').simulate('click');

  expect(node.find('.popoverContent').exists()).toBe(true);
});

it('should open/close popover based on button trigger', () => {
  const node = mount(
    <Popover trigger={<Popover.Button>test</Popover.Button>}>Child content</Popover>
  );

  expect(node.find('.popoverContent').exists()).toBe(false);

  node.find(Popover.Button).simulate('click');

  expect(node.find('.popoverContent').exists()).toBe(true);
});
