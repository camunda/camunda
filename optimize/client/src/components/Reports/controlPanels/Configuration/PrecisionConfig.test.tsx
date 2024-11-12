/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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

  legend.find({labelText: 'Custom precision'}).simulate('toggle', true);

  expect(spy).toHaveBeenCalledWith({precision: {$set: 1}});
});

it('should change the precision', () => {
  props.configuration.precision = 5;

  const spy = jest.fn();
  const node = shallow(<PrecisionConfig {...props} onChange={spy} />);

  node.find('.precision').simulate('keydown', {key: '3'});

  expect(spy).toHaveBeenCalledWith({precision: {$set: 3}});
});
