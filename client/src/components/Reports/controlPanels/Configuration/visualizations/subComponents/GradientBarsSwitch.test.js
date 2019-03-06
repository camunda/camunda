import React from 'react';
import {shallow} from 'enzyme';

import GradientBarsSwitch from './GradientBarsSwitch';

it('should contain a switch that is checked if gradient bars are shown', () => {
  const node = shallow(<GradientBarsSwitch configuration={{showGradientBars: true}} />);

  expect(node.find('Switch').prop('checked')).toBe(true);

  node.setProps({configuration: {showGradientBars: false}});

  expect(node.find('Switch').prop('checked')).toBe(false);
});

it('should call the onChange method when toggling the switch', () => {
  const spy = jest.fn();

  const node = shallow(
    <GradientBarsSwitch configuration={{showGradientBars: true}} onChange={spy} />
  );

  node.find('Switch').simulate('change', {target: {checked: false}});

  expect(spy).toHaveBeenCalledWith({showGradientBars: {$set: false}});
});
