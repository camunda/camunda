/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import update from 'immutability-helper';
import {shallow} from 'enzyme';
import {Input, LabeledInput, Select} from 'components';

import BucketSize from './BucketSize';

const report = {
  data: {
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
  },
  result: {
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
    <BucketSize
      report={{data: {...report.data, groupBy: {type: 'variable', value: {type: 'Date'}}}}}
    />
  );

  expect(node).toMatchSnapshot();
});

it('should render bucket options', () => {
  const node = shallow(<BucketSize report={report} />);

  expect(node).toMatchSnapshot();
});

it('should invoke onChange when triggering the activation switch', () => {
  const spy = jest.fn();

  const node = shallow(<BucketSize report={report} onChange={spy} />);

  node.find('Switch').prop('onChange')({target: {checked: true}});

  expect(spy.mock.calls[0][0].customBucket.active).toEqual({$set: true});
});

it('should reevaluate the report when changing the size or baseline to a valid value', () => {
  const spy = jest.fn();

  const node = shallow(<BucketSize report={report} onChange={spy} />);

  node.find(Input).prop('onChange')({target: {value: '-50'}});
  expect(spy).not.toHaveBeenCalled();
  expect(node.find('Message').dive()).toIncludeText('positive');

  node.find(LabeledInput).prop('onChange')({target: {value: '-1'}});

  expect(spy).toHaveBeenCalledWith({customBucket: {baseline: {$set: '-1'}}}, true);
});

it('should include a unit selection when report is grouped by duration', () => {
  const durationReport = update(report, {data: {groupBy: {$set: {type: 'duration'}}}});

  const node = shallow(<BucketSize report={durationReport} />);

  expect(node.find(Select)).toExist();
});

it('should include a unit selection when report is distributed by number variable', () => {
  const distributedByVariableReport = {
    data: {
      groupBy: {type: 'startDate'},
      distributedBy: {type: 'variable', value: {type: 'Double'}},
      configuration: {
        distributeByCustomBucket: report.data.configuration.customBucket,
      },
    },
  };
  const node = shallow(<BucketSize report={distributedByVariableReport} />);

  expect(node.find('.BucketSize')).toExist();
});

it('should find an apropriate bucket size and baseline when enabling the switch the first time', () => {
  const spy = jest.fn();
  const node = shallow(<BucketSize report={report} onChange={spy} />);

  node.find('Switch').prop('onChange')({target: {checked: true}});

  expect(spy).toHaveBeenCalledWith(
    {customBucket: {active: {$set: true}, baseline: {$set: 1}, bucketSize: {$set: 0.1}}},
    true
  );
});

it('should disable the switch and set the tooltip message when disabled', () => {
  const node = shallow(<BucketSize report={report} disabled />);

  expect(node.find('Switch').prop('title')).toBe(
    'This function only works with automatic preview update turned on'
  );
  expect(node.find('Switch').prop('disabled')).toBe(true);
});
