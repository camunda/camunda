/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import {NumberInput} from '@carbon/react';

import {CarbonSelect} from 'components';

import BucketSize from './BucketSize';

const props = {
  groupBy: {type: 'variable', value: {type: 'Integer'}},
  distributedBy: {
    type: 'none',
    value: null,
  },
  configuration: {
    customBucket: {
      active: false,
      bucketSize: '10.0',
      bucketSizeUnit: 'minute',
      baseline: '0.0',
      baselineUnit: 'minute',
    },
  },
  reportResult: {
    measures: [
      {
        data: [{key: '1'}, {key: '2'}, {key: 'missing'}],
      },
    ],
  },
};

jest.mock('debounce', () => jest.fn((fn) => fn));

it('should render nothing if the current variable is not a number', () => {
  const node = shallow(
    <BucketSize {...props} groupBy={{type: 'variable', value: {type: 'Date'}}} />
  );

  expect(node.text()).toBe('');
});

it('should render bucket options', () => {
  const node = shallow(<BucketSize {...props} />);

  expect(node.find('FormGroup').prop('legendText').props).toEqual({
    id: 'bucketSizeToggle',
    labelA: 'Bucket Size',
    labelB: 'Bucket Size',
    labelText: undefined,
    onToggle: expect.any(Function),
    size: 'sm',
    toggled: false,
  });
  expect(node.find(NumberInput).at(0).props()).toEqual({
    defaultValue: '-',
    disabled: true,
    id: 'bucketSize',
    label: 'Size',
    invalid: false,
    invalidText: 'Enter a positive number',
    onBlur: expect.any(Function),
    onChange: expect.any(Function),
  });
  expect(node.find(NumberInput).at(1).props()).toEqual({
    defaultValue: '-',
    disabled: true,
    id: 'bucketSizeBaseline',
    invalid: false,
    invalidText: 'Must be a valid number',
    label: 'Baseline',
    onBlur: expect.any(Function),
    onChange: expect.any(Function),
  });
});

it('should invoke onChange when triggering the activation switch', () => {
  const spy = jest.fn();

  const node = shallow(<BucketSize {...props} onChange={spy} />);

  node.find('FormGroup').prop('legendText').props.onToggle(true);

  expect(spy.mock.calls[0][0].customBucket.active).toEqual({$set: true});
});

it('should reevaluate the report when changing the size or baseline to a valid value', () => {
  const spy = jest.fn();

  const node = shallow(<BucketSize {...props} onChange={spy} />);

  node.find(NumberInput).at(0).prop('onChange')(undefined, {value: '-50'});
  expect(spy).not.toHaveBeenCalled();
  expect(node.find(NumberInput).at(0).prop('invalidText')).toBe('Enter a positive number');

  node.find(NumberInput).at(1).prop('onChange')(undefined, {value: '-1'});

  expect(spy).toHaveBeenCalledWith({customBucket: {baseline: {$set: '-1'}}}, true);
});

it('should include a unit selection when report is grouped by duration', () => {
  const node = shallow(
    <BucketSize {...props} groupBy={{type: 'duration', value: {type: 'Integer'}}} />
  );

  expect(node.find(CarbonSelect)).toExist();
});

it('should include a unit selection when report is distributed by number variable', () => {
  const distributedByVariableReport = {
    groupBy: {type: 'startDate'},
    distributedBy: {type: 'variable', value: {type: 'Double'}},
    configuration: {
      distributeByCustomBucket: {
        active: false,
        bucketSize: '10.0',
        bucketSizeUnit: 'minute',
        baseline: '0.0',
        baselineUnit: 'minute',
      },
    },
  };
  const node = shallow(<BucketSize {...props} {...distributedByVariableReport} />);

  expect(node.find('.BucketSize')).toExist();
});

it('should find an apropriate bucket size and baseline when enabling the switch the first time', () => {
  const spy = jest.fn();
  const node = shallow(<BucketSize {...props} onChange={spy} />);

  node.find('FormGroup').prop('legendText').props.onToggle(true);

  expect(spy).toHaveBeenCalledWith(
    {customBucket: {active: {$set: true}, baseline: {$set: 1}, bucketSize: {$set: 0.1}}},
    true
  );
});

it('should disable the switch and set the tooltip message when disabled', () => {
  const node = shallow(<BucketSize {...props} disabled />);

  expect(node.find('FormGroup').prop('legendText').props.labelText).toBe(
    'This function only works with automatic preview update turned on'
  );
  expect(node.find('FormGroup').prop('legendText').props.disabled).toBe(true);
});
