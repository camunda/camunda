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
          configuration: {aggregationTypes: ['avg']},
        },
        name: 'Report 1',
      },
      {
        id: '2',
        data: {
          visualization: 'number',
          view: {properties: ['duration']},
          configuration: {aggregationTypes: ['avg']},
        },
        name: 'Report 2',
      },
      {combined: true, id: '3', data: {visualization: 'number'}, name: 'Report 3'},
    ]),
    loadAlerts: jest.fn().mockReturnValue([
      {
        id: 'alertID',
        emails: ['test@hotmail.com'],
        name: 'Some Alert',
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
  const node = shallow(<AlertsDropdown {...props} />);

  runAllEffects();

  node.find(Dropdown.Option).at(1).simulate('click');

  const updatedAlert = {id: 'alertID', name: 'newName'};
  node.find(AlertModal).prop('onConfirm')(updatedAlert);

  expect(editAlert).toHaveBeenCalledWith('alertID', updatedAlert);
  expect(node.find(AlertModal)).not.toExist();
});

it('should pass only reports in scope', async () => {
  const node = shallow(<AlertsDropdown {...props} dashboardReports={[{id: '2'}]} />);

  runAllEffects();

  node.find(Dropdown.Option).at(1).simulate('click');

  expect(node.find(AlertModal).prop('reports').length).toBe(1);
  expect(node.find(AlertModal).prop('reports')[0].id).toBe('2');
});

it('should pass number report id to alert modal', async () => {
  const node = shallow(<AlertsDropdown {...props} numberReport={{id: '2'}} />);

  runAllEffects();

  node.find(Dropdown.Option).at(1).simulate('click');

  expect(node.find(AlertModal).prop('initialReport')).toBe('2');
});

it('should delete an alert', async () => {
  const node = shallow(<AlertsDropdown {...props} />);

  runAllEffects();

  node.find(Dropdown.Option).at(1).simulate('click');
  node.find(AlertModal).prop('onRemove')();

  expect(node.find(Deleter).prop('entity').id).toBe('alertID');

  node.find(Deleter).prop('deleteEntity')({id: 'alertID'});
  await flushPromises();

  expect(removeAlert).toHaveBeenCalledWith('alertID');
});
