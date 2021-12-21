/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {getOptimizeProfile} from 'config';

import AggregationType from './AggregationType';

jest.mock('config', () => ({
  getOptimizeProfile: jest.fn().mockReturnValue('platform'),
}));

it('should render nothing if the current result is no duration and the view is not variable', () => {
  const node = shallow(
    <AggregationType
      report={{
        configuration: {aggregationTypes: ['avg']},
        view: {entity: null, properties: ['rawData']},
        distributedBy: {type: 'none'},
      }}
    />
  );

  expect(node).toMatchSnapshot();
});

it('should render an aggregation selection for duration reports', () => {
  const node = shallow(
    <AggregationType
      report={{
        view: {properties: ['duration']},
        distributedBy: {type: 'none'},
        configuration: {aggregationTypes: ['median']},
      }}
    />
  );

  expect(node).toMatchSnapshot();
});

it('should render an user task duration selection for user task duration reports', async () => {
  const node = shallow(
    <AggregationType
      report={{
        view: {entity: 'userTask', properties: ['duration']},
        distributedBy: {type: 'none'},
        configuration: {aggregationTypes: ['median'], userTaskDurationTimes: ['idle']},
      }}
    />
  );

  await runAllEffects();

  expect(node).toMatchSnapshot();
});

it('should render sum field for variable reports', () => {
  const node = shallow(
    <AggregationType
      report={{
        view: {entity: 'variable', properties: [{}]},
        distributedBy: {type: 'none'},
        configuration: {aggregationTypes: ['sum']},
      }}
    />
  );

  expect(node.find('Switch').first()).toHaveProp('label', 'Sum');
});

it('should hide sum field for incident reports', () => {
  const node = shallow(
    <AggregationType
      report={{
        view: {entity: 'incident', properties: ['duration']},
        distributedBy: {type: 'none'},
        configuration: {aggregationTypes: ['avg']},
      }}
    />
  );

  expect(node.find('Switch').first()).not.toHaveProp('label', 'Sum');
});

it('should reevaluate the report when changing the aggregation type', () => {
  const spy = jest.fn();

  const node = shallow(
    <AggregationType
      report={{
        view: {properties: ['duration']},
        distributedBy: {type: 'none'},
        configuration: {aggregationTypes: ['median']},
      }}
      onChange={spy}
    />
  );

  node
    .find('Switch')
    .last()
    .simulate('change', {target: {checked: true}});

  expect(spy).toHaveBeenCalledWith(
    {
      configuration: {
        aggregationTypes: {$set: ['median', 'max']},
        targetValue: {active: {$set: false}},
      },
    },
    true
  );
});

it('should hide median aggregation if processpart is defined', () => {
  const spy = jest.fn();

  const node = shallow(
    <AggregationType
      report={{
        view: {properties: ['duration']},
        distributedBy: {type: 'none'},
        configuration: {aggregationTypes: ['avg'], processPart: 'defined'},
      }}
      onChange={spy}
    />
  );

  expect(node.find({label: 'Median'})).not.toExist();
});

it('should disable median aggregation for reports distributed by process', () => {
  const spy = jest.fn();

  const node = shallow(
    <AggregationType
      report={{
        view: {properties: ['duration']},
        distributedBy: {type: 'process'},
        configuration: {aggregationTypes: ['avg']},
      }}
      onChange={spy}
    />
  );

  expect(node.find({label: 'Median'})).toExist();
  expect(node.find({label: 'Median'}).prop('disabled')).toBe(true);
});

it('should not show user task duration selection for user task duration reports in cloud environment', async () => {
  getOptimizeProfile.mockReturnValueOnce('cloud');
  const node = shallow(
    <AggregationType
      report={{
        view: {entity: 'userTask', properties: ['duration']},
        distributedBy: {type: 'none'},
        configuration: {aggregationTypes: ['avg'], userTaskDurationTimes: ['total']},
      }}
    />
  );

  await runAllEffects();

  expect(node.find({label: 'Work'})).not.toExist();
  expect(node.find({label: 'Idle'})).not.toExist();
});
