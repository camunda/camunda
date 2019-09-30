/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Select} from 'components';
import {reportConfig} from 'services';

import ReportSelect from './ReportSelect';

jest.mock('services', () => {
  const rest = jest.requireActual('services');

  return {
    ...rest,
    reportConfig: {
      ...rest.reportConfig,
      process: {
        getLabelFor: jest.fn().mockReturnValue('foo'),
        options: {
          view: [{key: 'rawData', data: 'foo', label: 'viewfoo'}],
          groupBy: [
            {key: 'none', data: 'foo', label: 'groupbyfoo'},
            {key: 'variable', options: 'inputVariable', label: 'Input Variable'}
          ],
          visualization: [{data: 'foo', label: 'visualizationfoo'}]
        },
        isAllowed: jest.fn().mockReturnValue(true)
      }
    }
  };
});

const config = {
  type: 'process',
  field: 'view',
  value: 'foo',
  variables: {inputVariable: []},
  disabled: false,
  onChange: jest.fn()
};

it('should disable options which would create a wrong combination', () => {
  reportConfig.process.isAllowed.mockReturnValue(false);

  const node = shallow(<ReportSelect {...config} />);

  expect(node.find(Select.Option)).toBeDisabled();
});

it('should disable the variable groupby submenu if there are no variables', () => {
  const node = shallow(<ReportSelect {...config} field="groupBy" />);

  expect(node.find(Select.Submenu)).toBeDisabled();
});

it('should match snapshot', async () => {
  const config = {
    type: 'process',
    field: 'view',
    value: 'foo',
    variables: {inputVariable: [{id: 'test', type: 'date', name: 'testName'}]},
    disabled: false,
    onChange: jest.fn()
  };

  const node = shallow(<ReportSelect {...config} field="groupBy" />);

  expect(node).toMatchSnapshot();
});

it('invoke onChange with the correct variable data', async () => {
  const config = {
    type: 'process',
    field: 'view',
    value: 'foo',
    variables: {inputVariable: [{id: 'test', type: 'date', name: 'testName'}]},
    disabled: false,
    onChange: jest.fn()
  };

  const node = shallow(<ReportSelect {...config} field="groupBy" />);

  node.props().onChange('inputVariable_test');

  expect(config.onChange).toHaveBeenCalledWith({
    type: 'inputVariable',
    value: {id: 'test', name: 'testName', type: 'date'}
  });
});
