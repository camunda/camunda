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
        update: jest.fn(),
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
      definitions: [{id: 'definitionId'}],
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

it('invoke configUpdate with the correct variable data', async () => {
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
  reportConfig.process.update.mockClear();
  reportConfig.process.update.mockReturnValueOnce({content: 'change'});
  reportConfig.process.findSelectedOption.mockReturnValueOnce({key: 'none', data: selectedOption});

  node.find(Select).simulate('change', 'variable_test');

  expect(reportConfig.process.update.mock.calls[0][1]).toBe(selectedOption);
  expect(spy).toHaveBeenCalledWith({content: 'change'}, true);
});

it('should use the distributedBy value when removing the groupBy', () => {
  const spy = jest.fn();
  const node = shallow(<GroupBy {...config} value="duration" onChange={spy} />);

  reportConfig.process.update.mockClear();
  node.find(Button).simulate('click');

  expect(reportConfig.process.update.mock.calls[0][1]).toEqual({type: 'distribution'});
});

describe('group by process', () => {
  it('should display a group by process option if its allowed', () => {
    const node = shallow(
      <GroupBy
        {...config}
        report={{
          data: {
            distributedBy: {type: 'none'},
            definitions: [{id: 'definitionId1'}, {id: 'definitionId2'}],
          },
        }}
      />
    );

    expect(node.find({value: 'process'})).toExist();
  });

  it('should set distribute by process if group by process is set', () => {
    const spy = jest.fn();
    const node = shallow(
      <GroupBy
        {...config}
        report={{
          data: {
            distributedBy: {type: 'none'},
            definitions: [{id: 'definitionId1'}, {id: 'definitionId2'}],
            configuration: {aggregationTypes: ['avg']},
          },
        }}
        onChange={spy}
      />
    );

    reportConfig.process.update.mockReturnValueOnce({configuration: {}});
    node.find(Select).simulate('change', 'process');

    expect(spy.mock.calls[0][0].distributedBy).toEqual({$set: {type: 'process', value: null}});
  });

  it('should remove median aggregation when grouping by process', () => {
    const spy = jest.fn();
    const node = shallow(
      <GroupBy
        {...config}
        report={{
          data: {
            distributedBy: {type: 'none'},
            definitions: [{id: 'definitionId1'}, {id: 'definitionId2'}],
            configuration: {aggregationTypes: ['avg', 'median', 'min']},
          },
        }}
        onChange={spy}
      />
    );

    reportConfig.process.update.mockReturnValueOnce({configuration: {}});
    node.find(Select).simulate('change', 'process');

    expect(spy.mock.calls[0][0].configuration.aggregationTypes).toEqual({$set: ['avg', 'min']});
  });

  it('should reset aggregation to average if median is the only aggregation when grouping by process', () => {
    const spy = jest.fn();
    const node = shallow(
      <GroupBy
        {...config}
        report={{
          data: {
            distributedBy: {type: 'none'},
            definitions: [{id: 'definitionId1'}, {id: 'definitionId2'}],
            configuration: {aggregationTypes: ['median']},
          },
        }}
        onChange={spy}
      />
    );

    reportConfig.process.update.mockReturnValueOnce({configuration: {}});
    node.find(Select).simulate('change', 'process');

    expect(spy.mock.calls[0][0].configuration.aggregationTypes).toEqual({$set: ['avg']});
  });
});
