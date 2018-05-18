import React from 'react';
import {mount} from 'enzyme';

import ProgressBar from './ProgressBar';

const props = {
  min: 0,
  max: 100,
  value: 20,
  formatter: () => 'formatted'
};

it('should use the provided formatter', () => {
  const node = mount(<ProgressBar {...props} />);

  expect(node).toIncludeText('formatted');
});

it('should fill according to the provided value, min and max properties', () => {
  const node = mount(<ProgressBar {...props} />);

  expect(node.find('.ProgressBar--filled')).toHaveProp('style', {width: '20%'});
});
