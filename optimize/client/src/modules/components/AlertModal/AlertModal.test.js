/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {isEmailEnabled} from 'config';
import {formatters, evaluateReport} from 'services';

import {AlertModal} from './AlertModal';
import ThresholdInput from './ThresholdInput';

jest.mock('config', () => ({
  isEmailEnabled: jest.fn().mockReturnValue(true),
  getOptimizeVersion: jest.fn().mockReturnValue('2.7.0'),
}));

jest.mock('services', () => {
  const rest = jest.requireActual('services');

  return {
    ...rest,
    evaluateReport: jest.fn().mockReturnValue({
      id: '6',
      data: {view: {entity: 'process instance', properties: ['duration']}},
      result: {data: 123},
    }),
    formatters: {
      ...rest.formatters,
      convertDurationToSingleNumber: jest.fn().mockReturnValue(723),
      convertDurationToObject: jest.fn().mockReturnValue({value: '14', unit: 'seconds'}),
    },
    getOptimizeVersion: jest.fn().mockReturnValue('2.4.0-alpha2'),
  };
});

beforeEach(() => {
  jest.clearAllMocks();
});

const initialAlert = {
  id: '71395',
  name: 'Sample Alert',
  emails: ['test@camunda.com', 'test@camunda.com'],
  reportId: '8',
  thresholdOperator: '<',
  threshold: 37,
  checkInterval: {
    value: 1,
    unit: 'hours',
  },
  reminder: null,
  fixNotification: true,
};

const reports = [
  {
    id: '5',
    name: 'Some Report',
    data: {view: {properties: ['frequency']}, visualization: 'number'},
  },
  {
    id: '8',
    name: 'Nice report',
    data: {view: {properties: ['frequency']}, visualization: 'number'},
  },
  {
    id: '9',
    name: 'Nice report',
    data: {view: {properties: ['duration']}, visualization: 'number'},
  },
  {
    id: '10',
    name: 'percentage report',
    data: {view: {properties: ['percentage']}, visualization: 'number'},
  },
];

const props = {
  reports,
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  generateDocsLink: (url) => url,
};

it('should apply the alert property to the state when changing props', () => {
  const node = shallow(<AlertModal {...props} />);

  node.setProps({initialAlert});

  expect(node.state()).toMatchSnapshot();
});

it('should call the onConfirm method and not include duplicate emails', () => {
  const spy = jest.fn();
  const node = shallow(<AlertModal {...props} onConfirm={spy} />);

  node.setProps({initialAlert});

  node.find('.confirm').simulate('click');

  expect(spy).toHaveBeenCalled();
  expect(spy.mock.calls[0][0].emails).toEqual(['test@camunda.com']);
});

it('should disable the submit button if the name is empty', () => {
  const node = shallow(<AlertModal {...props} />);

  node.setProps({initialAlert});
  node.setState({name: ''});

  expect(node.find('.confirm')).toBeDisabled();
});

it('should disable the submit button if the email is not valid', () => {
  const node = shallow(<AlertModal {...props} />);

  node.setProps({initialAlert});
  node.find('MultiEmailInput').prop('onChange')(['this is not a valid email'], false);
  expect(node.find('.confirm')).toBeDisabled();
});

it('should disable the submit button if no report is selected', () => {
  const node = shallow(<AlertModal {...props} />);

  node.setProps({initialAlert});
  node.setState({reportId: ''});
  expect(node.find('.confirm')).toBeDisabled();
});

it('should disable the submit button if the threshold is not a number', () => {
  const node = shallow(<AlertModal {...props} />);

  node.setProps({initialAlert});
  node.setState({threshold: 'five'});
  expect(node.find('.confirm')).toBeDisabled();
});

it('should disable the submit button if the threshold is not a percentage', () => {
  const node = shallow(<AlertModal {...props} initialAlert={{...initialAlert, reportId: '10'}} />);

  node.setState({threshold: '101'});
  expect(node.find(ThresholdInput).prop('invalid')).toBe(true);
  expect(node.find('.confirm')).toBeDisabled();

  node.setState({threshold: '100'});
  expect(node.find(ThresholdInput).prop('invalid')).toBe(false);
  expect(node.find('.confirm')).not.toBeDisabled();
});

it('should disable the submit button if the check interval is negative', () => {
  const node = shallow(<AlertModal {...props} />);

  node.setProps({initialAlert});
  node.setState({
    checkInterval: {
      value: '-7',
      unit: 'seconds',
    },
  });
  expect(node.find('.confirm')).toBeDisabled();
});

it('should show warning that email is not configured', async () => {
  isEmailEnabled.mockReturnValue(false);
  const node = await shallow(<AlertModal {...props} />);

  expect(node.find('ActionableNotification').exists()).toBe(true);
});

it('should not display warning if email is configured', async () => {
  isEmailEnabled.mockReturnValue(true);
  const node = await shallow(<AlertModal {...props} />);
  await node.instance().componentDidMount();
  await node.update();

  expect(node.find('ActionableNotification').exists()).toBe(false);
});

it('should convert a duration threshold when opening', async () => {
  const node = await shallow(<AlertModal {...props} />);

  await node.instance().componentDidMount();

  node.setProps({
    initialAlert: {
      name: 'New Alert',
      id: '1234',
      emails: [],
      reportId: '9',
      thresholdOperator: '>',
      threshold: '14000',
      checkInterval: {
        value: '10',
        unit: 'minutes',
      },
      reminder: null,
      fixNotification: false,
    },
  });

  expect(node.state('threshold')).toEqual({value: '14', unit: 'seconds'});
  expect(formatters.convertDurationToObject).toHaveBeenCalledWith('14000');
});

it('should convert a duration threshold when confirming', async () => {
  const spy = jest.fn();
  const node = await shallow(<AlertModal {...props} onConfirm={spy} />);
  node.setState({threshold: {value: '723', unit: 'milliseconds'}});

  node.instance().confirm();

  expect(spy).toHaveBeenCalled();
  expect(spy.mock.calls[0][0].threshold).toBe(723);
});

it('should contain a threshold input', () => {
  const node = shallow(<AlertModal {...props} />);

  expect(node.find(ThresholdInput)).toExist();
});

it('should pass the selected report as initial value to the combobox', () => {
  const node = shallow(<AlertModal {...props} initialAlert={initialAlert} />);

  expect(node.find('#report').props().selectedItem).toBe(reports[1]);
});

it('should display report value', () => {
  const node = shallow(<AlertModal {...props} />);
  evaluateReport.mockClear();

  node.find('#report').prop('onChange')({selectedItem: {id: '6'}});

  expect(evaluateReport).toHaveBeenCalledWith('6');
  expect(shallow(node.find('#report').prop('helperText'))).toIncludeText('123');
});

it('should display report value for variable reports', () => {
  evaluateReport.mockReturnValueOnce({
    id: '6',
    data: {view: {entity: 'variable', properties: [{type: 'double', name: 'doubleVariable'}]}},
    result: {data: 123.123},
  });
  const node = shallow(<AlertModal {...props} />);

  node.find('#report').prop('onChange')({selectedItem: {id: '6'}});

  expect(shallow(node.find('#report').prop('helperText'))).toIncludeText('123.123');
});

it('should load an initial report if specified', () => {
  const node = shallow(<AlertModal {...props} initialReport="5" />);

  expect(node.find('#report').prop('selectedItem')).toBe(reports[0]);
  expect(node.find('#report').prop('disabled')).toBe(true);
});

it('should not load the report twice if both initialAlert and initialReport are defined', () => {
  const node = shallow(<AlertModal {...props} initialReport="5" initialAlert={initialAlert} />);

  expect(evaluateReport.mock.calls.length).toBe(1);
  expect(node.find('#report').prop('selectedItem')).toBe(reports[1]);
});

it('should allow to remove an alert from inside the modal if onRemove prop is provided', () => {
  const spy = jest.fn();
  const node = shallow(<AlertModal {...props} onRemove={spy} />);

  node.find('.deleteAlertButton').simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should disable the submit button when disabled prop is passed', () => {
  const node = shallow(<AlertModal {...props} disabled />);

  expect(node.find('Button').at(1)).toBeDisabled();
});
