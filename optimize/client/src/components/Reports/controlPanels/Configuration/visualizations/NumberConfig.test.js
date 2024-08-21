/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';
import update from 'immutability-helper';

import {track} from 'tracking';

import NumberConfig from './NumberConfig';

jest.mock('tracking', () => ({track: jest.fn()}));

const props = {
  report: {
    id: 'reportID',
    data: {
      definitions: [{}],
      view: {properties: ['frequency']},
      configuration: {
        precision: null,
        targetValue: {
          active: false,
          countProgress: {baseline: '0', target: '100'},
          durationProgress: {
            baseline: {
              value: '0',
              unit: 'hours',
            },
            target: {
              value: '2',
              unit: 'hours',
            },
          },
        },
        aggregationTypes: [{type: 'avg', value: null}],
      },
    },
    result: {measures: [{}]},
  },
};

it('should not show precision selection for percentage reports', () => {
  const node = shallow(
    <NumberConfig
      report={update(props.report, {data: {view: {properties: {$set: ['percentage']}}}})}
    />
  );

  expect(node.find('.precision')).not.toExist();
});

it('should set the report as kpi report', () => {
  const spy = jest.fn();
  const node = shallow(<NumberConfig {...props} onChange={spy} />);

  node.find({labelText: 'Display as a process KPI'}).simulate('change', undefined, {checked: true});

  expect(spy).toHaveBeenCalledWith({targetValue: {isKpi: {$set: true}}});
  expect(track).toHaveBeenCalledWith('displayAsProcessKpiEnabled', {entityId: 'reportID'});
});

it('should not show kpi config for reports with variable view', () => {
  const node = shallow(
    <NumberConfig
      {...props}
      report={update(props.report, {data: {view: {entity: {$set: 'variable'}}}})}
    />
  );

  expect(node.find({label: 'Display as a process KPI'})).not.toExist();
});

it('should not show kpi config for multi process reports', () => {
  const node = shallow(
    <NumberConfig
      {...props}
      report={update(props.report, {data: {definitions: {$set: [{}, {}]}}})}
    />
  );

  expect(node.find({label: 'Display as a process KPI'})).not.toExist();
});

it('should not show target input for multi-measure reports', () => {
  const node = shallow(
    <NumberConfig
      {...props}
      report={update(props.report, {result: {measures: {$set: [{}, {}]}}})}
    />
  );

  expect(node.isEmptyRender()).toBe(true);
});
