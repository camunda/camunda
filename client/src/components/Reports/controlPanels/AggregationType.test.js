/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Select} from 'components';

import AggregationType from './AggregationType';

it('should render nothing if the current result is no duration and the view is not variable', () => {
  const node = shallow(
    <AggregationType
      report={{data: {view: {entity: null, properties: ['rawData']}}, result: {type: 'rawData'}}}
    />
  );

  expect(node).toMatchSnapshot();
});

it('should render an aggregation selection for duration reports', () => {
  const node = shallow(
    <AggregationType
      report={{
        data: {view: {properties: ['duration']}, configuration: {aggregationType: 'median'}},
      }}
    />
  );

  expect(node).toMatchSnapshot();
});

it('should render an additional sum field for variable reports', () => {
  const node = shallow(
    <AggregationType
      report={{
        data: {
          view: {entity: 'variable', properties: [{}]},
          configuration: {aggregationType: 'sum'},
        },
      }}
    />
  );

  expect(node.find(Select.Option).first()).toHaveProp('value', 'sum');
});

it('should not crash when no resultType is set (e.g. for combined reports)', () => {
  shallow(<AggregationType report={{result: {}}} />);
});

it('should reevaluate the report when changing the aggregation type', () => {
  const spy = jest.fn();

  const node = shallow(
    <AggregationType
      report={{
        data: {view: {properties: ['duration']}, configuration: {aggregationType: 'median'}},
      }}
      onChange={spy}
    />
  );

  node.find('Select').simulate('change', 'max');

  expect(spy).toHaveBeenCalledWith({configuration: {aggregationType: {$set: 'max'}}}, true);
});

it('should hide median aggregation if processpart is defined', () => {
  const spy = jest.fn();

  const node = shallow(
    <AggregationType
      report={{
        data: {
          view: {properties: ['duration']},
          configuration: {aggregationType: 'avg', processPart: 'defined'},
        },
      }}
      onChange={spy}
    />
  );

  expect(node.find({value: 'median'})).not.toExist();
});
