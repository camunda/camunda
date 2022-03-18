/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {AlertModal, Dropdown, Deleter} from 'components';
import {loadReports, loadAlerts, editAlert, addAlert, removeAlert, getCollection} from 'services';
import {getWebhooks} from 'config';

import {AlertsDropdown} from './AlertsDropdown';

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    loadReports: jest.fn().mockReturnValue([
      {
        id: '1',
        data: {
          visualization: 'table',
          view: {properties: ['frequency']},
          configuration: {aggregationTypes: [{type: 'avg', value: null}]},
        },
        name: 'Report 1',
      },
      {
        id: '2',
        data: {
          visualization: 'number',
          view: {properties: ['duration']},
          configuration: {aggregationTypes: [{type: 'avg', value: null}]},
        },
        name: 'Report 2',
      },
      {combined: true, id: '3', data: {visualization: 'number'}, name: 'Report 3'},
      {
        id: '4',
        data: {
          visualization: 'number',
          view: {properties: ['duration']},
          configuration: {aggregationTypes: [{type: 'avg', value: null}]},
        },
        name: 'Report 4',
      },
    ]),
    loadAlerts: jest.fn().mockReturnValue([
      {
        id: 'alert1',
        emails: ['test@hotmail.com'],
        name: 'first Alert',
        lastModifier: 'Admin',
        lastModified: '2017-11-11T11:11:11.1111+0200',
        reportId: '4',
        webhook: null,
      },
      {
        id: 'alert2',
        emails: ['test@hotmail.com'],
        name: 'second Alert',
        lastModifier: 'Admin',
        lastModified: '2017-11-11T11:11:11.1111+0200',
        reportId: '2',
        webhook: null,
      },
    ]),
    addAlert: jest.fn(),
    editAlert: jest.fn(),
    removeAlert: jest.fn(),
    getCollection: jest.fn().mockReturnValue('collectionId'),
  };
});

jest.mock('config', () => ({getWebhooks: jest.fn().mockReturnValue(['webhook1', 'webhook2'])}));
jest.mock('notifications', () => ({addNotification: jest.fn()}));

beforeEach(() => {
  jest.clearAllMocks();
});

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  location: {},
  dashboardReports: [],
};

it('should load existing alerts, reports and webhooks', () => {
  shallow(<AlertsDropdown {...props} />);

  runAllEffects();

  expect(loadReports).toHaveBeenCalled();
  expect(loadAlerts).toHaveBeenCalled();
  expect(getWebhooks).toHaveBeenCalled();
});

it('should only show the dropdown inside a collection', () => {
  getCollection.mockReturnValueOnce(null);
  const node = shallow(<AlertsDropdown {...props} />);

  runAllEffects();

  expect(node.find(Dropdown)).not.toExist();
  expect(loadAlerts).not.toHaveBeenCalled();
});

it('should create new alert', async () => {
  const testAlert = {id: 'alertID', name: 'newName'};
  const node = shallow(<AlertsDropdown {...props} />);

  runAllEffects();

  node.find(Dropdown.Option).at(0).simulate('click');

  node.find(AlertModal).prop('onConfirm')(testAlert);

  expect(addAlert).toHaveBeenCalledWith(testAlert);
  expect(node.find(AlertModal)).not.toExist();
});

it('should edit an alert', async () => {
  const node = shallow(<AlertsDropdown {...props} dashboardReports={[{id: '4'}]} />);

  runAllEffects();

  node.find(Dropdown.Option).at(1).simulate('click');

  const updatedAlert = {id: 'alert1', name: 'newName'};
  node.find(AlertModal).prop('onConfirm')(updatedAlert);

  expect(editAlert).toHaveBeenCalledWith('alert1', updatedAlert);
  expect(node.find(AlertModal)).not.toExist();
});

it('should pass only reports in scope', async () => {
  const node = shallow(<AlertsDropdown {...props} dashboardReports={[{id: '2'}]} />);

  runAllEffects();

  node.find(Dropdown.Option).at(1).simulate('click');

  expect(node.find(AlertModal).prop('reports').length).toBe(1);
  expect(node.find(AlertModal).prop('reports')[0].id).toBe('2');
});

it('should show only alerts in scope', async () => {
  const node = shallow(<AlertsDropdown {...props} dashboardReports={[{id: '2'}]} />);

  runAllEffects();

  expect(node.find(Dropdown.Option).length).toBe(2);
  expect(node.find(Dropdown.Option).at(1)).toIncludeText('second Alert');
});

it('should pass number report id to alert modal', async () => {
  const node = shallow(
    <AlertsDropdown {...props} dashboardReports={undefined} numberReport={{id: '2'}} />
  );

  runAllEffects();

  node.find(Dropdown.Option).at(1).simulate('click');

  expect(node.find(AlertModal).prop('initialReport')).toBe('2');
});

it('should delete an alert', async () => {
  const node = shallow(<AlertsDropdown {...props} dashboardReports={[{id: '4'}]} />);

  runAllEffects();

  node.find(Dropdown.Option).at(1).simulate('click');
  node.find(AlertModal).prop('onRemove')();

  expect(node.find(Deleter).prop('entity').id).toBe('alert1');

  node.find(Deleter).prop('deleteEntity')({id: 'alert1'});
  await flushPromises();

  expect(removeAlert).toHaveBeenCalledWith('alert1');
});
