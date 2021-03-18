/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import AggregationType from './AggregationType';

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

it('should render an user task duration selection for user task duration reports', () => {
  const node = shallow(
    <AggregationType
      report={{
        view: {entity: 'userTask', properties: ['duration']},
        distributedBy: {type: 'none'},
        configuration: {aggregationTypes: ['median'], userTaskDurationTimes: ['idle']},
      }}
    />
  );

  expect(node).toMatchSnapshot();
});

it('should render an additional sum field for variable reports', () => {
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
        aggregationType: {$set: 'median'},
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

it('should reset the visualization to table if the report is distributed', () => {
  const spy = jest.fn();

  const node = shallow(
    <AggregationType
      report={{
        view: {properties: ['duration']},
        distributedBy: {type: 'assignee'},
        configuration: {aggregationTypes: ['avg']},
        visualization: 'bar',
      }}
      onChange={spy}
    />
  );

  node
    .find('Switch')
    .last()
    .simulate('change', {target: {checked: true}});

  expect(spy.mock.calls[0][0].visualization).toEqual({$set: 'table'});
});
