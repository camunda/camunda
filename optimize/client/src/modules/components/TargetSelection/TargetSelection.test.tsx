/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';
import update from 'immutability-helper';

import {Report} from 'types';

import TargetSelection from './TargetSelection';

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
  } as unknown as Report,
  onChange: jest.fn(),
};

beforeEach(() => {
  jest.clearAllMocks();
});

it('should contain a target input for count property', () => {
  const node = shallow(<TargetSelection {...props} />);

  expect(node.find('CountTargetInput')).toExist();
});

it('should contain a target input for variable reports', () => {
  const variableReportProps = update(props, {report: {data: {view: {entity: {$set: 'variable'}}}}});
  const node = shallow(<TargetSelection {...variableReportProps} />);

  expect(node.find('CountTargetInput')).toExist();
});

it('should contain a duration target input for duration property', () => {
  const node = shallow(
    <TargetSelection
      {...props}
      report={update(props.report, {data: {view: {properties: {$set: ['duration']}}}})}
    />
  );

  expect(node.find('CountTargetInput')).not.toExist();
  expect(node.find('DurationTargetInput')).toExist();
});

it('should contain a target input for count property', () => {
  const node = shallow(<TargetSelection {...props} />);

  node.find('CountTargetInput').simulate('change', 'target', '500');
  expect(props.onChange).toHaveBeenCalledWith({
    targetValue: {countProgress: {target: {$set: '500'}}},
  });
});
