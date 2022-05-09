/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Button} from 'components';

import BarLineConfig from './BarLineConfig';

const report = {
  combined: false,
  data: {
    visualization: 'bar',
    view: {properties: ['frequency']},
    groupBy: {},
    distributedBy: {type: 'none', value: null},
    configuration: {
      measureVisualizations: {
        frequency: 'bar',
        duration: 'line',
      },
    },
  },
  result: {
    measures: [{data: []}],
  },
};

it('should show point markers and barchart config', () => {
  const node = shallow(<BarLineConfig report={report} />);

  expect(node.find('PointMarkersConfig')).toExist();
  expect(node.find('BarChartConfig')).toExist();
});

it('should select measure visualization according to configuration', () => {
  const node = shallow(<BarLineConfig report={report} />);
  const isButtonActive = (measureIdx, buttonIdx) =>
    node.find('.measureContainer').at(measureIdx).find(Button).at(buttonIdx).prop('active');

  expect(isButtonActive(0, 0)).toBe(false);
  expect(isButtonActive(0, 1)).toBe(true);
  expect(isButtonActive(1, 0)).toBe(true);
  expect(isButtonActive(1, 1)).toBe(false);
});

it('should change measure visualization on button click', () => {
  const spy = jest.fn();
  const node = shallow(<BarLineConfig report={report} onChange={spy} />);

  node.find('.measureContainer').at(0).find(Button).at(0).simulate('click');
  expect(spy.mock.calls[0][0].measureVisualizations.$set).toEqual({
    frequency: 'line',
    duration: 'bar',
  });

  node.find('.measureContainer').at(1).find(Button).at(0).simulate('click');
  expect(spy.mock.calls[1][0].measureVisualizations.$set).toEqual({
    frequency: 'bar',
    duration: 'line',
  });
});
