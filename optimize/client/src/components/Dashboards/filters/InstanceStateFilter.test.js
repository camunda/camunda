/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {shallow} from 'enzyme';
import {Button, Toggle} from '@carbon/react';

import {Popover} from 'components';

import InstanceStateFilter from './InstanceStateFilter';

jest.mock('services', () => {
  return {
    ...jest.requireActual('services'),
    incompatibleFilters: ([{type}]) => type === 'completedInstancesOnly',
  };
});

const props = {
  filter: [],
  setFilter: jest.fn(),
};

beforeEach(() => {
  props.setFilter.mockClear();
});

it('should contain a popover to set instance state filters', () => {
  const node = shallow(<InstanceStateFilter {...props} />);

  expect(node.find(Popover)).toExist();

  node
    .find(Toggle)
    .at(0)
    .simulate('toggle', {target: {checked: true}});
  expect(props.setFilter).toHaveBeenCalledWith([
    {type: 'runningInstancesOnly', data: null, filterLevel: 'instance'},
  ]);
});

it('should show the filter state', () => {
  const node = shallow(
    <InstanceStateFilter {...props} filter={[{type: 'runningInstancesOnly'}]} />
  );

  expect(node.find(Toggle).at(0)).toHaveProp('toggled', true);
  const popoverButton = shallow(node.find(Popover).prop('trigger'));
  expect(popoverButton.text()).toContain('Running');
  expect(popoverButton.find('span.indicator.active')).toExist();
});

it('should reset the filter state', () => {
  const node = shallow(
    <InstanceStateFilter
      {...props}
      filter={[{type: 'runningInstancesOnly'}, {type: 'instanceStartDate'}]}
    />
  );

  node.find(Button).simulate('click');

  expect(props.setFilter).toHaveBeenCalledWith([{type: 'instanceStartDate'}]);
});

it('should disable the reset button if no filter is active', () => {
  const node = shallow(<InstanceStateFilter {...props} filter={[{type: 'instanceStartDate'}]} />);

  expect(node.find(Button)).toHaveProp('disabled', true);
});

it('should disable incompatible filters', () => {
  const node = shallow(<InstanceStateFilter {...props} />);

  expect(node.find(Toggle).at(0)).toHaveProp('disabled', false);
  expect(node.find(Toggle).at(1)).toHaveProp('disabled', true);
});

it('should render children', () => {
  const node = shallow(
    <InstanceStateFilter {...props}>
      <div className="childContent" />
    </InstanceStateFilter>
  );

  expect(node.find('.childContent')).toExist();
});
