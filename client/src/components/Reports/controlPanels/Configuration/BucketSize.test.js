/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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
        bucketSize: '10',
        bucketSizeUnit: 'minute',
        baseline: '0',
        baselineUnit: 'minute',
      },
    },
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

it('should active the bucket size when enabling the switch', () => {
  const spy = jest.fn();

  const node = shallow(<BucketSize report={report} onChange={spy} />);

  node.find('Switch').prop('onChange')({target: {checked: true}});

  expect(spy).toHaveBeenCalledWith({customBucket: {active: {$set: true}}}, true);
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
