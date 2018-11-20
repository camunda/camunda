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

it('should reset to defaults when property changes', () => {
  expect(
    HeatmapConfig.onUpdate(
      {report: {data: {view: {property: 'new'}}}},
      {report: {data: {view: {property: 'prev'}}}}
    )
  ).toEqual(HeatmapConfig.defaults);
});

it('should reset to defaults when visualization type changes', () => {
  expect(
    HeatmapConfig.onUpdate(
      {type: 'prev', report: {data: {view: {property: 'test'}}}},
      {type: 'new', report: {data: {view: {property: 'test'}}}}
    )
  ).toEqual(HeatmapConfig.defaults);
});
