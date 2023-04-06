/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {shallow} from 'enzyme';

import DashboardRenderer from './DashboardRenderer';

const tiles = [
  {
    position: {x: 0, y: 0},
    dimensions: {width: 3, height: 1},
    id: '1',
    type: 'optimize_report',
  },
  {
    position: {x: 2, y: 0},
    dimensions: {width: 1, height: 4},
    id: '2',
    type: 'external_url',
  },
  {
    position: {x: 3, y: 1},
    dimensions: {width: 2, height: 2},
    id: '3',
    type: 'text',
  },
];

it('should render a Dashboard Report for every Report in the props', () => {
  const node = shallow(<DashboardRenderer tiles={tiles} />);

  expect(node.find('DashboardReport').at(0).prop('report')).toBe(tiles[0]);
  expect(node.find('DashboardReport').at(1).prop('report')).toBe(tiles[1]);
  expect(node.find('DashboardReport').at(2).prop('report')).toBe(tiles[2]);
});

it('should set min width and height to 1 for text tiles', () => {});
