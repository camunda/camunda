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
        findSelectedOption: jest.fn().mockReturnValue({key: 'none', data: 'foo'}),
        getLabelFor: jest.fn().mockReturnValue('foo'),
        options: {
          view: [
            {key: 'rawData', data: 'foo'},
            {
              key: 'pi',
              options: [
                {
                  key: 'pi_count',
                  group: 'pi_count',
                  data: {properties: ['frequency'], entity: 'processInstance'},
                },
                {
                  key: 'pi_duration',
                  group: 'pi_duration',
                  data: {properties: ['duration'], entity: 'processInstance'},
                },
              ],
            },
            {key: 'variable', options: 'variable'},
          ],
          groupBy: [
            {key: 'none', data: 'foo'},
            {key: 'variable', options: 'inputVariable'},
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
  field: 'view',
  value: 'foo',
  variables: {inputVariable: [], variable: []},
  disabled: false,
  onChange: jest.fn(),
};

it('should disable options which would create a wrong combination', () => {
  reportConfig.process.isAllowed.mockReturnValue(false);

  const node = shallow(<ReportSelect {...config} />);

  expect(node.find(Select.Option).first()).toBeDisabled();
});

it('should disable the variable groupby submenu if there are no variables', () => {
  const node = shallow(<ReportSelect {...config} field="groupBy" />);

  expect(node.find(Select.Submenu)).toBeDisabled();
});

it('should disable the variable view submenu if there are no variables', () => {
  const node = shallow(<ReportSelect {...config} />);

  expect(node.find(Select.Submenu)).toBeDisabled();
});

it('should match snapshot', async () => {
  const config = {
    type: 'process',
    field: 'view',
    value: 'foo',
    variables: {inputVariable: [{id: 'test', type: 'date', name: 'testName'}]},
    disabled: false,
    onChange: jest.fn(),
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
    onChange: jest.fn(),
  };

  const node = shallow(<ReportSelect {...config} field="groupBy" />);

  const selectedOption = {
    type: 'inputVariable',
    value: {id: 'test', name: 'testName', type: 'date'},
  };
  reportConfig.process.findSelectedOption.mockReturnValueOnce({key: 'none', data: selectedOption});

  node.props().onChange('inputVariable_test');

  expect(config.onChange).toHaveBeenCalledWith(selectedOption);
});

it('provide the correct payload for variable reports', async () => {
  const config = {
    type: 'process',
    field: 'view',
    value: 'foo',
    variables: {variable: [{type: 'Integer', name: 'testName'}]},
    disabled: false,
    onChange: jest.fn(),
  };

  const node = shallow(<ReportSelect {...config} />);

  const selectedOption = {
    entity: 'variable',
    property: {name: 'testName', type: 'Integer'},
  };
  reportConfig.process.findSelectedOption.mockReturnValueOnce({key: 'none', data: selectedOption});

  node.find('Select').simulate('change', 'variable_testName');

  expect(config.onChange).toHaveBeenCalledWith(selectedOption);
});

it('should not show suboptions for view measures', () => {
  const node = shallow(
    <ReportSelect {...config} value={{properties: ['frequency'], entity: 'processInstance'}} />
  );

  expect(
    node
      .findWhere((t) => t.text() === 'Process Instance')
      .parent()
      .type()
  ).toBe(Select.Option);
});
