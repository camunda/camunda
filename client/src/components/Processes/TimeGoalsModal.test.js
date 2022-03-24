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
  durationGoals: {
    goals: [
      {
        type: 'targetDuration',
        percentile: '25',
        value: '1',
        unit: 'weeks',
      },
      {
        type: 'slaDuration',
        percentile: '95',
        value: '5',
        unit: 'days',
      },
    ],
  },
};

it('should load initialGoals', async () => {
  const node = shallow(<TimeGoalsModal {...props} process={processWithGoals} />);

  await runAllEffects();

  expect(node.find(Select).at(0).prop('value')).toBe('25');
  expect(node.find(Input).at(0).prop('value')).toBe('1');
  expect(node.find('.unitSelection').at(0).prop('value')).toBe('weeks');
  expect(node.find(Select).at(2).prop('value')).toBe('95');
});

it('should use default goals if one of the initial goals is not present', async () => {
  const node = shallow(
    <TimeGoalsModal
      {...props}
      process={{
        ...processWithGoals,
        durationGoals: {goals: [processWithGoals.durationGoals.goals[1]]},
      }}
    />
  );

  await runAllEffects();

  expect(node.find(Select).at(0).prop('value')).toBe('75');
  expect(node.find(Select).at(2).prop('value')).toBe('95');
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

it('should invoke onConfirm when saving goals', async () => {
  const spy = jest.fn();
  const node = shallow(<TimeGoalsModal {...props} onConfirm={spy} />);

  await runAllEffects();

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
    {type: 'targetDuration', percentile: '75', unit: 'days', value: '20'},
    {type: 'slaDuration', percentile: '99', unit: 'days', value: '3'},
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

  node.find(Deleter).simulate('close');
  expect(node.find(Deleter).prop('entity')).toEqual();
  expect(removeSpy).toHaveBeenCalled();
  expect(spy).toHaveBeenCalled();
});

it('should filter out hidden goals when saving', async () => {
  const spy = jest.fn();
  const node = shallow(<TimeGoalsModal {...props} onConfirm={spy} process={processWithGoals} />);

  await runAllEffects();

  node
    .find('[type="checkbox"]')
    .at(0)
    .simulate('change', {target: {checked: false}});
  node.find('[primary]').simulate('click');

  expect(spy).toHaveBeenCalledWith([processWithGoals.durationGoals.goals[1]]);
});

it('should disable save button and show an error message if duration input is invalid', async () => {
  const node = shallow(
    <TimeGoalsModal
      {...props}
      process={{
        ...processWithGoals,
        durationGoals: {goals: [{...processWithGoals.durationGoals.goals[0], value: '-1'}]},
      }}
    />
  );

  await runAllEffects();

  expect(node.find('.positiveIntegerError')).toExist();
  expect(node.find('[primary]')).toBeDisabled();
});
