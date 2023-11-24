/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ComponentProps} from 'react';
import {shallow} from 'enzyme';

import PrecisionConfig from './PrecisionConfig';

const props: ComponentProps<typeof PrecisionConfig> = {
  view: {properties: ['frequency']},
  configuration: {
    precision: null,
  },
  onChange: jest.fn(),
};

it('should have a switch for the precision setting', () => {
  const spy = jest.fn();
  const node = shallow(<PrecisionConfig {...props} onChange={spy} />);

  const LegendElement = () => node.find('FormGroup').prop<JSX.Element>('legendText');
  const legend = shallow(<LegendElement />);

  expect(legend.find('Toggle')).toExist();
  expect(node.find('.precision')).toExist();

  legend.find({labelA: 'Custom Precision'}).simulate('toggle', true);

  expect(spy).toHaveBeenCalledWith({precision: {$set: 1}});
});

it('should change the precision', () => {
  props.configuration.precision = 5;

  const spy = jest.fn();
  const node = shallow(<PrecisionConfig {...props} onChange={spy} />);

  node.find('.precision').simulate('keydown', {key: '3'});

  expect(spy).toHaveBeenCalledWith({precision: {$set: 3}});
});
