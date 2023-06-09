/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';

import {Button, Tooltip} from 'components';

import CarbonPopover from './CarbonPopover';

it('opens and closes on button click', () => {
  const node = shallow(
    <CarbonPopover title="Popover Title">
      <div>Popover Content</div>
    </CarbonPopover>
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
    <CarbonPopover title="Foobar">
      <div>Popover Content</div>
    </CarbonPopover>
  );

  expect(node.find(Button)).toIncludeText('Foobar');
});

it('should specify the open button as icon button if it has an icon, but no title', () => {
  const node = shallow(
    <CarbonPopover icon="open">
      <div>Popover Content</div>
    </CarbonPopover>
  );

  expect(node.find(Button).prop('icon')).toBe(true);
});

it('should do not display child content initially', () => {
  const node = shallow(<CarbonPopover title="a">Child content</CarbonPopover>);

  expect(node).not.toIncludeText('Child content');
});

it('should automatically open the popover on mount with the autoOpen prop', () => {
  const node = shallow(
    <CarbonPopover title="a" autoOpen>
      Child content
    </CarbonPopover>
  );

  expect(node).toIncludeText('Child content');
});

it('should pass tooltip props to the tooltip component', () => {
  const node = shallow(
    <CarbonPopover title="a" tooltip="test" tooltipPosition="bottom">
      Child content
    </CarbonPopover>
  );

  expect(node.find(Tooltip).prop('content')).toBe('test');
  expect(node.find(Tooltip).prop('position')).toBe('bottom');
});
