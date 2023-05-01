/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';

import {Button} from '../Button';

import Tabs from './Tabs';

it('should match snapshot', () => {
  const node = shallow(
    <Tabs>
      <Tabs.Tab title="tab1 title">Tab1 content</Tabs.Tab>
      <Tabs.Tab title="tab2 title">Tab2 content</Tabs.Tab>
      <Tabs.Tab title="disable title" disabled={true}>
        disabled content
      </Tabs.Tab>
    </Tabs>
  );

  expect(node).toMatchSnapshot();
});

it('should change tab on click', () => {
  const spy = jest.fn();
  const node = shallow(
    <Tabs onChange={spy}>
      <Tabs.Tab value="tab1" title="tab1 title">
        Tab1 content
      </Tabs.Tab>
      <Tabs.Tab value="tab2" title="tab2 title">
        Tab2 content
      </Tabs.Tab>
    </Tabs>
  );

  expect(node.contains('Tab2 content')).toBe(false);
  node.find(Button).at(1).simulate('click');
  expect(spy).toHaveBeenCalledWith('tab2');
  expect(node.contains('Tab2 content')).toBe(true);
});

it('should hide buttonGroup if specified', () => {
  const node = shallow(
    <Tabs showButtons={false}>
      <Tabs.Tab title="tab1 title">Tab1 content</Tabs.Tab>
      <Tabs.Tab title="tab2 title">Tab2 content</Tabs.Tab>
    </Tabs>
  );

  expect(node.find('ButtonGroup')).not.toExist();
});
