/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';
import {runAllEffects} from 'react';

import {EntityList, AlertModal, Deleter} from 'components';
import {loadReports, loadAlerts, addAlert} from 'services';

import AlertList from './AlertList';
import CopyAlertModal from './modals/CopyAlertModal';

jest.mock('hooks', () => ({
  useErrorHandling: jest.fn().mockImplementation(() => ({
    mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  })),
}));

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
      {id: '3', data: {visualization: 'number'}, name: 'Report 3'},
    ]),
    loadAlerts: jest.fn().mockReturnValue([
      {
        id: 'alertID',
        emails: ['test@hotmail.com'],
        name: 'Some Alert',
        lastModifier: 'Admin',
        lastModified: '2017-11-11T11:11:11.1111+0200',
        reportId: '2',
      },
    ]),
    addAlert: jest.fn(),
    editAlert: jest.fn(),
    removeAlert: jest.fn(),
  };
});

const props = {};

it('should load existing reports', () => {
  shallow(<AlertList {...props} />);
  runAllEffects();

  expect(loadReports).toHaveBeenCalled();
});

it('should only save single number reports', async () => {
  const node = shallow(<AlertList {...props} />);
  runAllEffects();

  node.find(EntityList).prop('rows')[0].actions[0].action();
  expect(node.find(AlertModal).prop('reports').length).toEqual(1);
});

it('should not show multi-measure reports', async () => {
  loadReports.mockReturnValueOnce([
    {
      id: '1',
      data: {
        visualization: 'number',
        view: {properties: ['duration']},
        configuration: {
          aggregationTypes: [
            {type: 'avg', value: null},
            {type: 'max', value: null},
          ],
        },
      },
      name: 'Report 1 - multi measure',
    },
    {
      id: '2',
      data: {
        visualization: 'number',
        view: {properties: ['frequency']},
        configuration: {
          aggregationTypes: [
            {type: 'avg', value: null},
            {type: 'max', value: null},
          ],
        },
      },
      name: 'Report 2 - single measure',
    },
    {
      id: '3',
      data: {
        visualization: 'number',
        view: {properties: ['frequency', 'duration']},
        configuration: {aggregationTypes: [{type: 'avg', value: null}]},
      },
      name: 'Report 3 - multi measure',
    },
  ]);
  const node = shallow(<AlertList {...props} />);
  runAllEffects();

  node.find(EntityList).prop('rows')[0].actions[0].action();
  expect(node.find(AlertModal).prop('reports').length).toEqual(1);
});

it('should format durations with value and unit', async () => {
  loadAlerts.mockReturnValueOnce([
    {
      id: 'a1',
      name: 'New Alert',
      emails: ['test@hotmail.com'],
      reportId: '2',
      thresholdOperator: '>',
      threshold: {
        value: '12',
        unit: 'seconds',
      },
      checkInterval: {
        value: '10',
        unit: 'minutes',
      },
      reminder: null,
      fixNotification: false,
    },
  ]);

  const node = shallow(<AlertList {...props} />);
  runAllEffects();

  expect(node.find(EntityList).prop('rows')[0].meta[1]).toContain('12s');
});

it('should set the loading', () => {
  loadAlerts.mockReturnValueOnce(null);
  const node = shallow(<AlertList {...props} />);
  runAllEffects();

  expect(node.find(EntityList).prop('isLoading')).toBe(true);
});

it('should load data', () => {
  shallow(<AlertList {...props} />);
  runAllEffects();

  expect(loadReports).toHaveBeenCalled();
});

it('should show information about alerts', async () => {
  const node = shallow(<AlertList {...props} />);
  runAllEffects();

  expect(node.find(EntityList).prop('rows')[0].name).toBe('Some Alert');
  expect(node.find(EntityList).prop('rows')[0].meta[0]).toBe('Report 2');
});

it('should show create Alert button', () => {
  const node = shallow(<AlertList {...props} />);
  runAllEffects();

  expect(node.find(EntityList).prop('action')).toMatchSnapshot();
});

it('should Alert to Deleter', async () => {
  const node = shallow(<AlertList {...props} />);
  runAllEffects();

  node.find(EntityList).prop('rows')[0].actions[2].action();

  expect(node.find(Deleter).prop('entity')).toEqual({
    id: 'alertID',
    emails: ['test@hotmail.com'],
    name: 'Some Alert',
    lastModifier: 'Admin',
    lastModified: '2017-11-11T11:11:11.1111+0200',
    reportId: '2',
  });
});

it('should open a modal when editing an alert', async () => {
  const node = shallow(<AlertList {...props} />);
  runAllEffects();

  node.find(EntityList).prop('rows')[0].actions[0].action();

  expect(node.find(AlertModal)).toExist();
  expect(node.find(AlertModal).prop('initialAlert')).toEqual({
    id: 'alertID',
    emails: ['test@hotmail.com'],
    name: 'Some Alert',
    lastModifier: 'Admin',
    lastModified: '2017-11-11T11:11:11.1111+0200',
    reportId: '2',
  });
});

it('should invoke addAlert when copying an alert', async () => {
  const node = shallow(<AlertList {...props} />);
  runAllEffects();

  node.find(EntityList).prop('rows')[0].actions[1].action();

  node.find(CopyAlertModal).prop('onConfirm')('testName');

  expect(addAlert).toHaveBeenCalledWith({
    id: 'alertID',
    emails: ['test@hotmail.com'],
    name: 'testName',
    lastModifier: 'Admin',
    lastModified: '2017-11-11T11:11:11.1111+0200',
    reportId: '2',
  });
});
