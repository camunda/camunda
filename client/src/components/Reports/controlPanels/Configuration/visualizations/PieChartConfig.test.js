/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import PieChartConfig from './PieChartConfig';

const configuration = {
  alwaysShowAbsolute: true,
  alwaysShowRelative: false,
};

const pieReport = {
  combined: false,
  data: {visualization: 'pie', view: {properties: ['frequency']}, configuration},
};

it('it should display correct configuration for piechart', () => {
  const node = shallow(<PieChartConfig report={pieReport} />);
  expect(node).toMatchSnapshot();
});
