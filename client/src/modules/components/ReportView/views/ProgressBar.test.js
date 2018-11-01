import React from 'react';
import {shallow} from 'enzyme';

import ProgressBar from './ProgressBar';

const props = {
  min: 0,
  max: 100,
  value: 20,
  formatter: () => 'formatted'
};

it('should use the provided formatter', () => {
  const node = shallow(<ProgressBar {...props} />);

  expect(node).toIncludeText('formatted');
});

it('should fill according to the provided value, min and max properties', () => {
  const node = shallow(<ProgressBar {...props} />);

  expect(node.find('.ProgressBar--filled')).toHaveProp('style', {width: '20%'});
});

it('should show the overlay with the goal value when the goal value is exceeded', () => {
  const props = {
    min: 0,
    max: 100,
    value: 150,
    formatter: () => 'formatted'
  };

  const node = shallow(<ProgressBar {...props} />);

  expect(node.find('.goalOverlay')).toIncludeText('Goal');
});
