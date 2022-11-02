/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';

import PrecisionConfig from './PrecisionConfig';

const props = {
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
};

it('should have a switch for the precision setting', () => {
  const spy = jest.fn();
  const node = shallow(<PrecisionConfig {...props} onChange={spy} />);

  expect(node.find('Switch')).toExist();
  expect(node.find('.precision')).toExist();

  node.find({label: 'Custom Precision'}).simulate('change', {target: {checked: true}});

  expect(spy).toHaveBeenCalledWith({precision: {$set: 1}});
});

it('should change the precision', () => {
  props.configuration.precision = 5;

  const spy = jest.fn();
  const node = shallow(<PrecisionConfig {...props} onChange={spy} />);

  node.find('.precision').simulate('keydown', {key: '3'});

  expect(spy).toHaveBeenCalledWith({precision: {$set: 3}});
});
