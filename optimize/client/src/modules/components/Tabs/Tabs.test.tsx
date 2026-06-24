/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';
import {Tab, TabPanels, Tabs as CarbonTabs, TabList, TabsSkeleton} from '@carbon/react';

import Tabs from './Tabs';

it('should display tabs properly', () => {
  const node = shallow(
    <Tabs>
      <Tabs.Tab value={0} title="tab1 title">
        Tab1 content
      </Tabs.Tab>
      <Tabs.Tab value={1} title="tab2 title">
        Tab2 content
      </Tabs.Tab>
      <Tabs.Tab value={2} title="disable title" disabled={true}>
        disabled content
      </Tabs.Tab>
    </Tabs>
  );

  const tabs = node.find(Tab);
  const panels = node.find(TabPanels);
  expect(tabs.at(0).text()).toBe('tab1 title');
  expect(tabs.at(1).text()).toBe('tab2 title');
  expect(tabs.at(2).text()).toBe('disable title');
  expect(panels.childAt(0).dive().text()).toBe('Tab1 content');
  expect(panels.childAt(1).dive().text()).toBe('Tab2 content');
  expect(panels.childAt(2).dive().text()).toBe('disabled content');
});

it('should change tab on click', () => {
  const spy = jest.fn();
  const node = shallow(
    <Tabs value={'tab1'} onChange={spy}>
      <Tabs.Tab value="tab1" title="tab1 title">
        Tab1 content
      </Tabs.Tab>
      <Tabs.Tab value="tab2" title="tab2 title">
        Tab2 content
      </Tabs.Tab>
    </Tabs>
  );

  node.simulate('change', {selectedIndex: 1});
  expect(spy).toHaveBeenCalledWith('tab2');
});

it('should hide tab buttons if specified', () => {
  const node = shallow(
    <Tabs showButtons={false}>
      <Tabs.Tab value={'a'} title="tab1 title">
        Tab1 content
      </Tabs.Tab>
      <Tabs.Tab value={'b'} title="tab2 title">
        Tab2 content
      </Tabs.Tab>
    </Tabs>
  );

  expect(node.find(TabList)).not.toExist();
});

it('should use index values if no value prop is provided', () => {
  const spy = jest.fn();
  const node = shallow(
    <Tabs onChange={spy}>
      <Tabs.Tab title="tab1 title">Tab1 content</Tabs.Tab>
      <Tabs.Tab title="tab2 title">Tab2 content</Tabs.Tab>
    </Tabs>
  );

  const tabs = node.find(CarbonTabs);

  expect(tabs.prop('selectedIndex')).toBe(0);
  node.simulate('change', {selectedIndex: 1});
  expect(spy).toHaveBeenCalledWith(1);
});

it('should show tabs skeleton if isLoading is set to true', () => {
  const node = shallow(
    <Tabs isLoading>
      <Tabs.Tab value={'a'} title="tab1 title">
        Tab1 content
      </Tabs.Tab>
      <Tabs.Tab value={'b'} title="tab2 title">
        Tab2 content
      </Tabs.Tab>
    </Tabs>
  );

  expect(node.find(TabList)).not.toExist();
  expect(node.find(TabsSkeleton)).toExist();
});
