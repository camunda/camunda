/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {mount, shallow} from 'enzyme';

import {Button, Labeled, Tooltip} from 'components';

import Popover from './Popover';

it('opens and closes on button click', () => {
  const node = shallow(
    <Popover title="Popover Title">
      <div>Popover Content</div>
    </Popover>
  );

  expect(node.find('.popoverContent').exists()).toBe(false);

  // Simulate click on the button to open the popover
  node.find(Button).simulate('click');
  expect(node.find('.popoverContent').exists()).toBe(true);

  // Simulate click on the button again to close the popover
  node.find(Button).simulate('click');
  expect(node.find('.popoverContent').exists()).toBe(false);
});

it('opens and closes on button click', () => {
  const node = shallow(
    <Popover title="Foobar">
      <div>Popover Content</div>
    </Popover>
  );

  expect(node.find(Button)).toIncludeText('Foobar');
});

it('should specify the open button as icon button if it has an icon, but no title', () => {
  const node = shallow(
    <Popover icon="open">
      <div>Popover Content</div>
    </Popover>
  );

  expect(node.find(Button).prop('icon')).toBe(true);
});

it('should do not display child content initially', () => {
  const node = shallow(<Popover title="a">Child content</Popover>);

  expect(node).not.toIncludeText('Child content');
});

it('should automatically open the popover on mount with the autoOpen prop', () => {
  const node = shallow(
    <Popover title="a" autoOpen>
      Child content
    </Popover>
  );

  expect(node).toIncludeText('Child content');
});

it('should pass tooltip props to the tooltip component', () => {
  const node = shallow(
    <Popover title="a" tooltip="test" tooltipPosition="bottom">
      Child content
    </Popover>
  );

  expect(node.find(Tooltip).prop('content')).toBe('test');
  expect(node.find(Tooltip).prop('position')).toBe('bottom');
});

it('should display a label if specified', () => {
  const node = shallow(
    <Popover title="a" label="testLabel">
      Child content
    </Popover>
  );

  expect(node.find(Labeled).prop('label')).toBe('testLabel');
});

it('should use the carbon listbox trigger if specified', () => {
  const node = mount(
    <Popover title="a" trigger={<Popover.ListBox label="test">test</Popover.ListBox>}>
      Child content
    </Popover>
  );

  expect(node.find('.popoverContent').exists()).toBe(false);

  node.find(Popover.ListBox).find('button').simulate('click');

  expect(node.find('.popoverContent').exists()).toBe(true);
});

it('should use the carbon button trigger if specified', () => {
  const node = mount(
    <Popover title="a" trigger={<Popover.Button>test</Popover.Button>}>
      Child content
    </Popover>
  );

  expect(node.find('.popoverContent').exists()).toBe(false);

  node.find(Popover.Button).simulate('click');

  expect(node.find('.popoverContent').exists()).toBe(true);
});
