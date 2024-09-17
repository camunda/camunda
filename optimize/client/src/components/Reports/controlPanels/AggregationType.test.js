/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {getOptimizeProfile} from 'config';

import AggregationType from './AggregationType';

jest.mock('config', () => ({
  getOptimizeProfile: jest.fn().mockReturnValue('ccsm'),
}));

it('should render nothing if the current result is no duration and the view is not variable', () => {
  const node = shallow(
    <AggregationType
      report={{
        configuration: {aggregationTypes: [{type: 'avg', value: null}]},
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
        configuration: {aggregationTypes: [{type: 'percentile', value: 50}]},
      }}
    />
  );

  expect(node).toMatchSnapshot();
});

it('should render a user task duration selection for user task duration reports', async () => {
  const node = shallow(
    <AggregationType
      report={{
        view: {entity: 'userTask', properties: ['duration']},
        distributedBy: {type: 'none'},
        configuration: {
          aggregationTypes: [{type: 'percentile', value: 50}],
          userTaskDurationTimes: ['idle'],
        },
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
        configuration: {aggregationTypes: [{type: 'sum', value: null}]},
      }}
    />
  );

  expect(node.find('Toggle').first()).toHaveProp('labelText', 'Sum');
});

it('should hide sum field for incident reports', () => {
  const node = shallow(
    <AggregationType
      report={{
        view: {entity: 'incident', properties: ['duration']},
        distributedBy: {type: 'none'},
        configuration: {aggregationTypes: [{type: 'avg', value: null}]},
      }}
    />
  );

  expect(node.find('Toggle').first()).not.toHaveProp('labelText', 'Sum');
});

it('should reevaluate the report when changing the aggregation type', () => {
  const spy = jest.fn();

  const node = shallow(
    <AggregationType
      report={{
        view: {properties: ['duration']},
        distributedBy: {type: 'none'},
        configuration: {aggregationTypes: [{type: 'percentile', value: 50}]},
      }}
      onChange={spy}
    />
  );

  node.find('Toggle').last().simulate('toggle', true);

  expect(spy).toHaveBeenCalledWith(
    {
      configuration: {
        aggregationTypes: {
          $set: [
            {type: 'percentile', value: 50},
            {type: 'percentile', value: 25},
          ],
        },
        targetValue: {active: {$set: false}},
      },
    },
    true
  );
});

it('should hide percentile aggregations if processpart is defined', () => {
  const spy = jest.fn();

  const node = shallow(
    <AggregationType
      report={{
        view: {properties: ['duration']},
        distributedBy: {type: 'none'},
        configuration: {
          aggregationTypes: [{type: 'avg', value: null}],
          processPart: 'defined',
        },
      }}
      onChange={spy}
    />
  );

  expect(node.find({label: 'P25'})).not.toExist();
  expect(node.find({label: 'P50'})).not.toExist();
  expect(node.find({label: 'P95'})).not.toExist();
});

it('should not show user task duration selection for user task duration reports in cloud environment', async () => {
  getOptimizeProfile.mockReturnValueOnce('cloud');
  const node = shallow(
    <AggregationType
      report={{
        view: {entity: 'userTask', properties: ['duration']},
        distributedBy: {type: 'none'},
        configuration: {
          aggregationTypes: [{type: 'avg', value: null}],
          userTaskDurationTimes: ['total'],
        },
      }}
    />
  );

  await runAllEffects();

  expect(node.find({label: 'Work'})).not.toExist();
  expect(node.find({label: 'Idle'})).not.toExist();
});

it('should not show percentile aggregations when report is grouped by process ', async () => {
  const node = shallow(
    <AggregationType
      report={{
        view: {entity: 'processInstance', properties: ['duration']},
        distributedBy: {type: 'process'},
        configuration: {
          aggregationTypes: [{type: 'avg', value: null}],
        },
      }}
    />
  );

  await runAllEffects();

  expect(node.find({label: 'P25'})).not.toExist();
  expect(node.find({label: 'P95'})).not.toExist();
});
