/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import {Input, LabeledInput} from 'components';

import NumVariableBucket from './NumVariableBucket';

const report = {
  data: {
    groupBy: {type: 'variable', value: {type: 'Integer'}},
    configuration: {
      customNumberBucket: {
        active: false,
        bucketSize: '10',
        baseline: '0',
      },
    },
  },
};

jest.mock('debounce', () => jest.fn((fn) => fn));

it('should render nothing if the current variable is not a number', () => {
  const node = shallow(
    <NumVariableBucket
      report={{data: {...report.data, groupBy: {type: 'variable', value: {type: 'Date'}}}}}
    />
  );

  expect(node).toMatchSnapshot();
});

it('should render bucket options', () => {
  const node = shallow(<NumVariableBucket report={report} />);

  expect(node).toMatchSnapshot();
});

it('should active the bucket size when enabling the switch', () => {
  const spy = jest.fn();

  const node = shallow(<NumVariableBucket report={report} onChange={spy} />);

  node.find('Switch').prop('onChange')({target: {checked: true}});

  expect(spy).toHaveBeenCalledWith({customNumberBucket: {active: {$set: true}}}, true);
});

it('should reevaluate the report when changing the size or baseline to a valid value', () => {
  const spy = jest.fn();

  const node = shallow(<NumVariableBucket report={report} onChange={spy} />);

  node.find(Input).prop('onChange')({target: {value: '-50'}});
  expect(spy).not.toHaveBeenCalled();
  expect(node.find('Message').dive()).toIncludeText('non-negative');

  node.find(LabeledInput).prop('onChange')({target: {value: '-1'}});

  expect(spy).toHaveBeenCalledWith({customNumberBucket: {baseline: {$set: '-1'}}}, true);
});
