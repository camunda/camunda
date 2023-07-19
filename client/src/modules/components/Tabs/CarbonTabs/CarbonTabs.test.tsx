/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';

import CarbonTabs from './CarbonTabs';
import {Tab, TabPanels, Tabs} from '@carbon/react';

it('should display tabs properly', () => {
  const node = shallow(
    <CarbonTabs>
      <CarbonTabs.Tab value={0} title="tab1 title">
        Tab1 content
      </CarbonTabs.Tab>
      <CarbonTabs.Tab value={1} title="tab2 title">
        Tab2 content
      </CarbonTabs.Tab>
      <CarbonTabs.Tab value={2} title="disable title" disabled={true}>
        disabled content
      </CarbonTabs.Tab>
    </CarbonTabs>
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
    <CarbonTabs value={'tab1'} onChange={spy}>
      <CarbonTabs.Tab value="tab1" title="tab1 title">
        Tab1 content
      </CarbonTabs.Tab>
      <CarbonTabs.Tab value="tab2" title="tab2 title">
        Tab2 content
      </CarbonTabs.Tab>
    </CarbonTabs>
  );

  node.simulate('change', {selectedIndex: 1});
  expect(spy).toHaveBeenCalledWith('tab2');
});

it('should hide tab buttons if specified', () => {
  const node = shallow(
    <CarbonTabs showButtons={false}>
      <CarbonTabs.Tab value={'a'} title="tab1 title">
        Tab1 content
      </CarbonTabs.Tab>
      <CarbonTabs.Tab value={'b'} title="tab2 title">
        Tab2 content
      </CarbonTabs.Tab>
    </CarbonTabs>
  );

  expect(node.find('ButtonGroup')).not.toExist();
});

it('should use index values if no value prop is provided', () => {
  const spy = jest.fn();
  const node = shallow(
    <CarbonTabs onChange={spy}>
      <CarbonTabs.Tab title="tab1 title">Tab1 content</CarbonTabs.Tab>
      <CarbonTabs.Tab title="tab2 title">Tab2 content</CarbonTabs.Tab>
    </CarbonTabs>
  );

  const tabs = node.find(Tabs);

  expect(tabs.prop('selectedIndex')).toBe(0);
  node.simulate('change', {selectedIndex: 1});
  expect(spy).toHaveBeenCalledWith(1);
});
