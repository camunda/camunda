/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import LineChartConfig from './LineChartConfig';

const configuration = {
  showInstanceCount: false,
  color: '#1991c8',
  pointMarkers: true,
  hideRelativeValue: false,
  hideAbsoluteValue: false,
  xLabel: '',
  yLabel: '',
  targetValue: {active: false},
};

const lineReport = {
  data: {visualization: 'line', view: {properties: ['frequency']}, configuration},
};

it('it should display correct configuration for linechart', () => {
  const node = shallow(<LineChartConfig report={lineReport} />);

  expect(node.find('PointMarkersConfig').props()).toEqual({
    configuration: {
      color: '#1991c8',
      hideAbsoluteValue: false,
      hideRelativeValue: false,
      pointMarkers: true,
      showInstanceCount: false,
      targetValue: {
        active: false,
      },
      xLabel: '',
      yLabel: '',
    },
    onChange: undefined,
  });

  expect(node.find('BarChartConfig').props()).toEqual({
    onChange: undefined,
    report: {
      data: {
        configuration: {
          color: '#1991c8',
          hideAbsoluteValue: false,
          hideRelativeValue: false,
          pointMarkers: true,
          showInstanceCount: false,
          targetValue: {
            active: false,
          },
          xLabel: '',
          yLabel: '',
        },
        view: {
          properties: ['frequency'],
        },
        visualization: 'line',
      },
    },
  });
});
