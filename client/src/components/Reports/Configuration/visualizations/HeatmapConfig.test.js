import React from 'react';
import {shallow} from 'enzyme';

import HeatmapConfig from './HeatmapConfig';

it('it should disable the relative switch when the view property is frequency', () => {
  const node = shallow(
    <HeatmapConfig
      report={{data: {view: {property: 'frequency'}}}}
      configuration={{}}
      onchange={() => {}}
    />
  );

  expect(node.find('RelativeAbsoluteSelection').props().relativeDisabled).toBe(false);
});

it('should pass the configuration to RelativeAbsoluteSelection', () => {
  const node = shallow(
    <HeatmapConfig
      report={{data: {view: {property: 'frequency'}}}}
      configuration={{test: 'test'}}
      onchange={() => {}}
    />
  );

  expect(node.find('RelativeAbsoluteSelection').props().configuration).toEqual({test: 'test'});
});
