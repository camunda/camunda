/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {AlertModal} from './AlertModal';

import ThresholdInput from './ThresholdInput';

import {formatters} from 'services';
import {isEmailEnabled} from 'config';

jest.mock('config', () => ({
  isEmailEnabled: jest.fn().mockReturnValue(true),
  getOptimizeVersion: jest.fn().mockReturnValue('2.7.0'),
}));

jest.mock('services', () => {
  const rest = jest.requireActual('services');

  return {
    ...rest,
    formatters: {
      convertDurationToSingleNumber: jest.fn().mockReturnValue(723),
      convertDurationToObject: jest.fn().mockReturnValue({value: '14', unit: 'seconds'}),
    },
    getOptimizeVersion: jest.fn().mockReturnValue('2.4.0-alpha2'),
  };
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
  webhook: null,
};

const reports = [
  {id: '5', name: 'Some Report', data: {view: {property: 'frequency'}, visualization: 'number'}},
  {id: '8', name: 'Nice report', data: {view: {property: 'frequency'}, visualization: 'number'}},
  {
    id: '9',
    name: 'Nice report',
    data: {view: {property: 'duration'}, visualization: 'number'},
  },
];

it('should apply the alert property to the state when changing props', () => {
  const node = shallow(<AlertModal reports={reports} />);

  node.setProps({initialAlert});

  expect(node.state()).toEqual({
    id: '71395',
    name: 'Sample Alert',
    emails: ['test@camunda.com', 'test@camunda.com'],
    reportId: '8',
    thresholdOperator: '<',
    threshold: '37',
    checkInterval: {
      value: '1',
      unit: 'hours',
    },
    reminder: null,
    fixNotification: true,
    invalid: false,
    validEmails: true,
    webhook: null,
    inactive: null,
  });
});

it('should call the onConfirm method and not include duplicate emails', () => {
  const spy = jest.fn();
  const node = shallow(<AlertModal reports={reports} onConfirm={spy} />);

  node.setProps({initialAlert});

  node.find('[primary]').simulate('click');

  expect(spy).toHaveBeenCalled();
  expect(spy.mock.calls[0][0].emails).toEqual(['test@camunda.com']);
});

it('should disable the submit button if the name is empty', () => {
  const node = shallow(<AlertModal reports={reports} />);

  node.setProps({initialAlert});
  node.setState({name: ''});

  expect(node.find('[primary]')).toBeDisabled();
});

it('should disable the submit button if the email is not valid', () => {
  const node = shallow(<AlertModal reports={reports} />);

  node.setProps({initialAlert});
  node.find('MultiEmailInput').prop('onChange')(['this is not a valid email'], false);
  expect(node.find('[primary]')).toBeDisabled();
});

it('should disable the submit button if no report is selected', () => {
  const node = shallow(<AlertModal reports={reports} />);

  node.setProps({initialAlert});
  node.setState({reportId: ''});
  expect(node.find('[primary]')).toBeDisabled();
});

it('should disable the submit button if the threshold is not a number', () => {
  const node = shallow(<AlertModal reports={reports} />);

  node.setProps({initialAlert});
  node.setState({threshold: 'five'});
  expect(node.find('[primary]')).toBeDisabled();
});

it('should disable the submit button if the check interval is negative', () => {
  const node = shallow(<AlertModal reports={reports} />);

  node.setProps({initialAlert});
  node.setState({
    checkInterval: {
      value: '-7',
      unit: 'seconds',
    },
  });
  expect(node.find('[primary]')).toBeDisabled();
});

it('should enable the submit button if webhook is selected', () => {
  const node = shallow(<AlertModal reports={reports} webhooks={['testWebhook']} />);

  node.setProps({initialAlert});
  node.setState({emails: ['']});
  node.find('Typeahead').at(1).prop('onChange')('testWebhook');

  expect(node.find({variant: 'primary'})).not.toBeDisabled();
});

it('should show warning if alert is inactive due to missing webhook', async () => {
  const node = await shallow(<AlertModal reports={reports} webhooks={[]} />);
  node.setProps({initialAlert: {...initialAlert, emails: [], webhook: 'nonExistingWebhook'}});

  expect(node.find('MessageBox').exists()).toBe(true);
});

it('should show warning that email is not configured', async () => {
  isEmailEnabled.mockReturnValue(false);
  const node = await shallow(<AlertModal reports={reports} />);

  expect(node.find('MessageBox').exists()).toBe(true);
});

it('should not display warning if email is configured', async () => {
  isEmailEnabled.mockReturnValue(true);
  const node = await shallow(<AlertModal reports={reports} />);
  await node.instance().componentDidMount();
  await node.update();

  expect(node.find('.AlertModal__configuration-warning').exists()).toBe(false);
});

it('should convert a duration threshold when opening', async () => {
  const node = await shallow(<AlertModal reports={reports} />);

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
  const node = await shallow(<AlertModal reports={reports} onConfirm={spy} />);
  node.setState({threshold: {value: '723', unit: 'milliseconds'}});

  node.instance().confirm();

  expect(spy).toHaveBeenCalled();
  expect(spy.mock.calls[0][0].threshold).toBe(723);
});

it('should contain a threshold input', () => {
  const node = shallow(<AlertModal reports={reports} />);

  expect(node.find(ThresholdInput)).toExist();
});

it('should pass the selected report as initial value to the typeahead', () => {
  const node = shallow(<AlertModal reports={reports} initialAlert={initialAlert} />);

  expect(node.find('Typeahead').props().initialValue).toBe(initialAlert.reportId);
});
