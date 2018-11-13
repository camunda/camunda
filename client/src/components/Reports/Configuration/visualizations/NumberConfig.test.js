import React from 'react';
import {shallow} from 'enzyme';

import NumberConfig from './NumberConfig';

const props = {
  report: {data: {view: {operation: 'count'}}},
  configuration: {precision: null, targetValue: {active: false, values: {baseline: 0, target: 100}}}
};

it('should have a switch for the precision setting', () => {
  const spy = jest.fn();
  const node = shallow(<NumberConfig {...props} onChange={spy} />);

  expect(node.find('Switch')).toBePresent();
  expect(node.find('.precision')).toBePresent();

  node
    .find('Switch')
    .first()
    .simulate('change', {target: {checked: true}});

  expect(spy).toHaveBeenCalledWith('precision', 1);
});

it('should change the precision', () => {
  props.configuration.precision = 5;

  const spy = jest.fn();
  const node = shallow(<NumberConfig {...props} onChange={spy} />);

  node.find('.precision').simulate('keydown', {key: '3'});

  expect(spy).toHaveBeenCalledWith('precision', 3);
});

it('should contain a target input for count operations', () => {
  const node = shallow(<NumberConfig {...props} />);

  expect(node.find('CountTargetInput')).toBePresent();
});

it('should contain a target input for duration operations', () => {
  props.report.data.view.operation = 'avg';
  const node = shallow(<NumberConfig {...props} />);

  expect(node.find('CountTargetInput')).not.toBePresent();
  expect(node.find('DurationTargetInput')).toBePresent();
});
