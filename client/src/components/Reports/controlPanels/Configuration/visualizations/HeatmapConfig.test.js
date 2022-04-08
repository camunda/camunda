/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import HeatmapConfig from './HeatmapConfig';

it('it should hide the relative switch when the view property is frequency', () => {
  const node = shallow(
    <HeatmapConfig
      report={{data: {view: {properties: ['frequency']}, configuration: {}}}}
      onchange={() => {}}
    />
  );

  expect(node.find('RelativeAbsoluteSelection').props().hideRelative).toBe(false);
});

it('should pass relevant configuration to RelativeAbsoluteSelection', () => {
  const node = shallow(
    <HeatmapConfig
      report={{
        data: {
          view: {properties: ['frequency']},
          configuration: {alwaysShowAbsolute: true, alwaysShowRelative: false, unrelated: true},
        },
      }}
      onchange={() => {}}
    />
  );

  const props = node.find('RelativeAbsoluteSelection').props();

  expect(props.absolute).toBe(true);
  expect(props.relative).toBe(false);
  expect(props.unrelated).toBe(undefined);
});
