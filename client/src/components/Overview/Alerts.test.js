/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Button} from 'components';

import AlertsWithErrorHandling from './Alerts';
import {load, loadAlerts} from './service';

const Alerts = AlertsWithErrorHandling.WrappedComponent;

const reports = [
  {id: '1', data: {visualization: 'table', view: {property: 'frequency'}}, name: 'Report 1'},
  {id: '2', data: {visualization: 'number', view: {property: 'duration'}}, name: 'Report 2'}
];

jest.mock('./service');

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data))
};

const alert = {
  id: 'alertID',
  name: 'Some Alert',
  lastModifier: 'Admin',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  reportId: '2'
};

beforeAll(() => {
  load.mockReturnValue(reports);
  loadAlerts.mockReturnValue([alert]);
});

it('should load existing reports', () => {
  shallow(<Alerts {...props} />);

  expect(load).toHaveBeenCalled();
});

it('should only save single number reports', async () => {
  const node = shallow(<Alerts {...props} />);

  await node.instance().componentDidMount();

  expect(node.state('reports').map(report => report.id)).toEqual(['2']);
});

it('should format durations with value and unit', async () => {
  const wrapper = shallow(<Alerts {...props} />);

  const newAlert = {
    name: 'New Alert',
    email: '',
    reportId: '2',
    thresholdOperator: '>',
    threshold: {
      value: '12',
      unit: 'seconds'
    },
    checkInterval: {
      value: '10',
      unit: 'minutes'
    },
    reminder: null,
    fixNotification: false
  };
  await wrapper.instance().componentDidMount();

  const node = shallow(wrapper.instance().renderMetadata(newAlert));

  expect(node).toIncludeText('12s');
});

it('should show a loading indicator', () => {
  const node = shallow(<Alerts {...props} />);

  node.setState({loading: true});

  expect(node.find('LoadingIndicator')).toBePresent();
});

it('should load data', () => {
  shallow(<Alerts {...props} />);

  expect(loadAlerts).toHaveBeenCalled();
});

it('should show information about alerts', () => {
  const node = shallow(<Alerts {...props} />);

  expect(node.find('.dataTitle')).toIncludeText('Some Alert');
});

it('should show no data indicator', () => {
  loadAlerts.mockReturnValueOnce([]);
  const node = shallow(<Alerts {...props} />);

  expect(node.find('NoEntities')).toBePresent();
});

it('should display error messages', () => {
  const node = shallow(<Alerts {...props} error="Something went wrong" />);

  expect(node.find('Message')).toBePresent();
});

it('should show create Alert button', () => {
  const node = shallow(<Alerts {...props} />);

  expect(node.find('.createButton')).toBePresent();
});

it('should show confirmation modal when deleting Alert', async () => {
  const node = shallow(<Alerts {...props} />);

  await node
    .find('.operations')
    .find(Button)
    .last()
    .simulate('click');

  expect(node.state('deleting')).toEqual(alert);
});

it('should open a modal when editing an alert', () => {
  const node = shallow(<Alerts {...props} />);

  node
    .find('.operations')
    .find(Button)
    .first()
    .simulate('click');

  expect(node.find('AlertModal')).toBePresent();
  expect(node.find('AlertModal').prop('entity')).toBe(alert);
});
