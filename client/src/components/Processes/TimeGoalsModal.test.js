/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {Input} from 'components';
import {evaluateReport} from 'services';

import {TimeGoalsModal} from './TimeGoalsModal';

jest.mock('./service', () => ({
  loadTenants: jest.fn().mockReturnValue([{tenants: [{id: null}, {id: 'engineering'}]}]),
}));

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  evaluateReport: jest.fn().mockReturnValue({result: {measures: [{data: []}]}}),
}));

beforeEach(() => {
  jest.clearAllMocks();
});

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

const goals = [
  {
    type: 'targetDuration',
    percentile: '80',
    value: '1',
    unit: 'weeks',
    visible: true,
  },
  {
    type: 'slaDuration',
    percentile: '20',
    value: '5',
    unit: 'days',
    visible: true,
  },
];

it('should load initialGoals', () => {
  const node = shallow(<TimeGoalsModal {...props} initialGoals={goals} />);

  expect(node.find(Input).at(0).prop('value')).toBe('80');
  expect(node.find(Input).at(1).prop('value')).toBe('1');
  expect(node.find('Select').at(0).prop('value')).toBe('weeks');
  expect(node.find('.singleGoal').at(1).find(Input).at(0).prop('value')).toBe('20');
});

it('should evaluate and pass report result to chart component', async () => {
  const data = [{key: '22.0', value: '10'}];
  evaluateReport.mockReturnValueOnce({result: {measures: [{data}]}});
  const node = shallow(<TimeGoalsModal {...props} processDefinitionKey="DefKey" />);

  await runLastEffect();

  expect(evaluateReport.mock.calls[0][0].data.definitions).toEqual([
    {key: 'DefKey', versions: ['all'], tenantIds: [null, 'engineering']},
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
  node
    .find(Input)
    .at(1)
    .simulate('change', {target: {value: '3'}});
  node.find('Select').at(0).simulate('change', 'days');

  node.find('[primary]').simulate('click');

  expect(spy).toHaveBeenCalledWith([
    {type: 'targetDuration', percentile: '20', unit: 'days', value: '3', visible: true},
    {type: 'slaDuration', percentile: '', unit: null, value: '', visible: true},
  ]);
});
