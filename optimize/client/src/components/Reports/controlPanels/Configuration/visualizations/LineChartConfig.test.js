/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
  combined: false,
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
      combined: false,
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
