/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Select, Button} from 'components';
import {reportConfig} from 'services';

import GroupBy from './GroupBy';

jest.mock('services', () => {
  const rest = jest.requireActual('services');

  return {
    ...rest,
    reportConfig: {
      ...rest.reportConfig,
      process: {
        findSelectedOption: jest.fn().mockReturnValue({key: 'none', data: 'foo'}),
        getLabelFor: jest.fn().mockReturnValue('foo'),
        options: {
          groupBy: [
            {key: 'none', data: {type: 'none'}},
            {key: 'duration', data: 'duration'},
            {key: 'userTasks', data: 'userTask'},
            {key: 'variable', options: 'variable'},
          ],
          visualization: [{data: 'foo'}],
        },
        isAllowed: jest.fn().mockReturnValue(true),
      },
    },
  };
});

const config = {
  type: 'process',
  value: {type: 'none'},
  variables: {variable: []},
  onChange: jest.fn(),
  report: {
    data: {
      distributedBy: {type: 'distribution'},
    },
  },
  view: 'defined',
};

it('should disable options which would create a wrong combination', () => {
  reportConfig.process.isAllowed.mockImplementation((report, view, data) => data !== 'duration');

  const node = shallow(<GroupBy {...config} />);

  expect(node.find(Select.Option).first()).toBeDisabled();
});

it('should disable the variable view submenu if there are no variables', () => {
  const node = shallow(<GroupBy {...config} />);

  expect(node.find(Select.Submenu)).toBeDisabled();
});

it('invoke onChange with the correct variable data', async () => {
  const spy = jest.fn();
  const node = shallow(
    <GroupBy
      {...config}
      variables={{variable: [{id: 'test', type: 'date', name: 'testName'}]}}
      onChange={spy}
    />
  );

  const selectedOption = {
    type: 'variable',
    value: {id: 'test', name: 'testName', type: 'date'},
  };
  reportConfig.process.findSelectedOption.mockReturnValueOnce({key: 'none', data: selectedOption});

  node.find(Select).simulate('change', 'variable_test');

  expect(spy).toHaveBeenCalledWith(selectedOption);
});

it('should use the distributedBy value when removing the groupBy', () => {
  const spy = jest.fn();
  const node = shallow(<GroupBy {...config} value="duration" onChange={spy} />);

  node.find(Button).simulate('click');

  expect(spy).toHaveBeenCalledWith({type: 'distribution'});
});
