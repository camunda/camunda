/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow, mount} from 'enzyme';

import {Dropdown, ActionItem} from 'components';
import {showPrompt} from 'prompt';

import {getVariableNames} from './service';

import {FiltersEdit} from './FiltersEdit';

const props = {
  availableFilters: [],
  setAvailableFilters: jest.fn(),
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  reports: [{id: 'reportId'}],
  persistReports: jest.fn(),
};

jest.mock('prompt', () => ({
  showPrompt: jest.fn().mockImplementation(async (config, cb) => await cb()),
}));

jest.mock('./service', () => ({
  getVariableNames: jest.fn(),
}));

beforeEach(() => {
  props.setAvailableFilters.mockClear();
  props.persistReports.mockClear();
  getVariableNames.mockClear();
});

it('should contain a dropdown to add more filters', () => {
  const node = shallow(<FiltersEdit {...props} />);

  expect(node.find(Dropdown)).toExist();

  node.find(Dropdown.Option).at(0).simulate('click');
  expect(props.setAvailableFilters).toHaveBeenCalledWith([{type: 'startDate'}]);
});

it('should show added filters', () => {
  const node = shallow(<FiltersEdit {...props} availableFilters={[{type: 'state'}]} />);

  expect(node.find(ActionItem)).toExist();
});

it('should allow removing existing filters', () => {
  const node = shallow(<FiltersEdit {...props} availableFilters={[{type: 'state'}]} />);

  node.find(ActionItem).simulate('click');

  expect(props.setAvailableFilters).toHaveBeenCalledWith([]);
});

it('should not allow adding the same filter twice', () => {
  const node = shallow(<FiltersEdit {...props} availableFilters={[{type: 'startDate'}]} />);

  expect(node.find(Dropdown.Option).at(0)).toBeDisabled();
});

it('should disable the variable option if there are no reports', () => {
  const node = shallow(<FiltersEdit {...props} />);

  expect(node.find(Dropdown.Option).last()).not.toBeDisabled();
  node.setProps({reports: []});
  expect(node.find(Dropdown.Option).last()).toBeDisabled();
});

it('should show a prompt to save the dashboard when adding a variable filter on a dashboard with unsaved reports', async () => {
  const node = shallow(
    <FiltersEdit {...props} reports={[{id: 'reportId'}, {report: {name: 'unsaved report'}}]} />
  );

  node.find(Dropdown.Option).last().simulate('click');

  await flushPromises();

  expect(showPrompt).toHaveBeenCalledTimes(1);
  expect(props.persistReports).toHaveBeenCalledTimes(1);
});

it('should fetch variable names', () => {
  const node = mount(<FiltersEdit {...props} />);

  expect(getVariableNames).toHaveBeenCalledWith(['reportId']);
});

it('should remove filters that are no longer valid', () => {
  getVariableNames.mockReturnValue([{type: 'String', name: 'a'}]);

  mount(
    <FiltersEdit
      {...props}
      availableFilters={[
        {type: 'variable', data: {name: 'a', type: 'String'}},
        {type: 'variable', data: {name: 'b', type: 'Boolean'}},
      ]}
    />
  );

  expect(props.setAvailableFilters).toHaveBeenCalledWith([
    {type: 'variable', data: {name: 'a', type: 'String'}},
  ]);
});

it('should include the allowed values for string and number variables', () => {
  const node = shallow(<FiltersEdit {...props} />);

  node.find(Dropdown.Option).last().simulate('click');

  const modal = node.find('.dashboardVariableFilter');

  expect(modal).toExist();

  const newFilter = {
    type: 'variable',
    data: {type: 'String', name: 'stringVar', data: {operator: 'in', values: ['aStringValue']}},
  };

  modal.prop('addFilter')(newFilter);
  expect(props.setAvailableFilters).toHaveBeenCalledWith([newFilter]);
});

it('should not include a data field for boolean and date variables', () => {
  const node = shallow(<FiltersEdit {...props} />);

  node.find(Dropdown.Option).last().simulate('click');

  const modal = node.find('.dashboardVariableFilter');

  modal.prop('addFilter')({
    type: 'variable',
    data: {type: 'Boolean', name: 'newVar', data: {value: true}},
  });
  expect(props.setAvailableFilters).toHaveBeenCalledWith([
    {
      type: 'variable',
      data: {
        type: 'Boolean',
        name: 'newVar',
      },
    },
  ]);

  modal.prop('addFilter')({
    type: 'variable',
    data: {
      type: 'Date',
      name: 'newVar',
      data: {type: 'relative', start: {value: 1, unit: 'years'}, end: null},
    },
  });
  expect(props.setAvailableFilters).toHaveBeenCalledWith([
    {
      type: 'variable',
      data: {
        type: 'Date',
        name: 'newVar',
      },
    },
  ]);
});
