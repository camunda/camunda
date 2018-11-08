import React from 'react';
import {shallow} from 'enzyme';

import Heatmap from './Heatmap';

it('should not pass true to relativeDisabled proberty', () => {
  const node = shallow(
    <Heatmap
      report={{data: {view: {property: 'frequency'}}}}
      configuration={{}}
      onchange={() => {}}
    />
  );

  expect(node.find('RelativeAbsoluteSelection').props().relativeDisabled).toBe(false);
});

it('should not pass the configuration to RelativeAbsoluteSelection', () => {
  const node = shallow(
    <Heatmap
      report={{data: {view: {property: 'frequency'}}}}
      configuration={{test: 'test'}}
      onchange={() => {}}
    />
  );

  expect(node.find('RelativeAbsoluteSelection').props().configuration).toEqual({test: 'test'});
});
