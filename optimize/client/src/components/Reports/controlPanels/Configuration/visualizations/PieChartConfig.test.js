/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import PieChartConfig from './PieChartConfig';

const configuration = {
  alwaysShowAbsolute: true,
  alwaysShowRelative: false,
};

const pieReport = {
  data: {visualization: 'pie', view: {properties: ['frequency']}, configuration},
};

it('it should display correct configuration for piechart', () => {
  const node = shallow(<PieChartConfig report={pieReport} />);

  expect(node.find('RelativeAbsoluteSelection').props()).toEqual({
    absolute: true,
    relative: false,
    hideRelative: false,
    onChange: expect.any(Function),
  });
});
