/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';
import update from 'immutability-helper';

import {SingleProcessReport} from 'types';

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
  } as unknown as SingleProcessReport,
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
