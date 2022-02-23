/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runAllEffects, runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {Deleter, Input, Select} from 'components';
import {evaluateReport} from 'services';

import {updateGoals} from './service';
import {TimeGoalsModal} from './TimeGoalsModal';

jest.mock('notifications', () => ({addNotification: jest.fn()}));

jest.mock('./service', () => ({
  loadTenants: jest.fn().mockReturnValue([{tenants: [{id: null}, {id: 'engineering'}]}]),
  updateGoals: jest.fn(),
}));

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  evaluateReport: jest.fn().mockReturnValue({result: {instanceCount: 0, measures: [{data: []}]}}),
}));

beforeEach(() => {
  jest.clearAllMocks();
});

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  process: {processDefinitionKey: 'defKey', processName: 'defName'},
};

const processWithGoals = {
  ...props.process,
  timeGoals: [
    {
      type: 'targetDuration',
      percentile: '75',
      value: '1',
      unit: 'weeks',
      visible: true,
    },
    {
      type: 'slaDuration',
      percentile: '99',
      value: '5',
      unit: 'days',
      visible: true,
    },
  ],
};

it('should load initialGoals', () => {
  const node = shallow(<TimeGoalsModal {...props} process={processWithGoals} />);

  expect(node.find(Select).at(0).prop('value')).toBe('75');
  expect(node.find(Input).at(0).prop('value')).toBe('1');
  expect(node.find('.unitSelection').at(0).prop('value')).toBe('weeks');
  expect(node.find(Select).at(2).prop('value')).toBe('99');
});

it('should evaluate and pass report result to chart component', async () => {
  const data = [{key: '22.0', value: '10'}];
  evaluateReport.mockReturnValueOnce({result: {instanceCount: 22, measures: [{data}]}});
  const node = shallow(<TimeGoalsModal {...props} />);

  await runAllEffects();

  expect(evaluateReport.mock.calls[0][0].data.definitions).toEqual([
    {key: 'defKey', versions: ['all'], tenantIds: [null, 'engineering']},
  ]);

  expect(node.find('DurationChart').prop('data')).toEqual(data);
});

it('should invoke onConfirm when saving goals', () => {
  const spy = jest.fn();
  const node = shallow(<TimeGoalsModal {...props} onConfirm={spy} />);

  node
    .find(Input)
    .at(0)
    .simulate('change', {target: {value: '20'}});
  node.find('.unitSelection').at(0).simulate('change', 'days');

  node
    .find(Input)
    .at(1)
    .simulate('change', {target: {value: '3'}});
  node.find('.unitSelection').at(1).simulate('change', 'days');

  node.find('[primary]').simulate('click');

  expect(spy).toHaveBeenCalledWith([
    {type: 'targetDuration', percentile: '75', unit: 'days', value: '20', visible: true},
    {type: 'slaDuration', percentile: '99', unit: 'days', value: '3', visible: true},
  ]);
});

it('should calculate default duration values based on percentiles', async () => {
  evaluateReport.mockReturnValueOnce({
    result: {
      instanceCount: 245,
      measures: [
        {
          data: [
            {key: '3.0', value: 2.0, label: '3.0'},
            {key: '5.0', value: 39.0, label: '5.0'},
            {key: '10.0', value: 93.0, label: '10.0'},
            {key: '20.0', value: 100.0, label: '20.0'},
            {key: '40.0', value: 10.0, label: '40.0'},
            {key: '50.0', value: 1.0, label: '50.0'},
          ],
        },
      ],
    },
  });

  const node = shallow(<TimeGoalsModal {...props} />);

  // evaluates the report
  await runAllEffects();
  // calculate the default duration values
  await runLastEffect();

  expect(node.find('.singleGoal').at(0).find(Input).prop('value')).toBe('40');
  expect(node.find('.singleGoal').at(1).find(Input).prop('value')).toBe('50');
});

it('should invoke removeGoals when confirming the delete modal', async () => {
  const spy = jest.fn();
  const removeSpy = jest.fn();
  const node = shallow(
    <TimeGoalsModal {...props} process={processWithGoals} onClose={spy} onRemove={removeSpy} />
  );

  await runAllEffects();

  node.find('.deleteButton').simulate('click');

  expect(node.find(Deleter).prop('entity')).toEqual(processWithGoals);

  await node.find(Deleter).prop('deleteEntity')();

  expect(updateGoals).toHaveBeenCalledWith('defKey', []);
  expect(spy).toHaveBeenCalled();

  node.find(Deleter).simulate('close');
  expect(node.find(Deleter).prop('entity')).toEqual();
  expect(removeSpy).toHaveBeenCalled();
});
