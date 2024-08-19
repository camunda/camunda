/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import BarLineConfig from './BarLineConfig';

const report = {
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
    node.find('RadioButtonGroup').at(measureIdx).find('RadioButton').at(buttonIdx).prop('checked');

  expect(isButtonActive(0, 0)).toBe(false);
  expect(isButtonActive(0, 1)).toBe(true);
  expect(isButtonActive(1, 0)).toBe(true);
  expect(isButtonActive(1, 1)).toBe(false);
});

it('should change measure visualization on button click', () => {
  const spy = jest.fn();
  const node = shallow(<BarLineConfig report={report} onChange={spy} />);

  node.find('RadioButtonGroup').at(0).find('RadioButton').at(0).simulate('click');
  expect(spy.mock.calls[0][0].measureVisualizations.$set).toEqual({
    frequency: 'line',
    duration: 'bar',
  });

  node.find('RadioButtonGroup').at(1).find('RadioButton').at(0).simulate('click');
  expect(spy.mock.calls[1][0].measureVisualizations.$set).toEqual({
    frequency: 'bar',
    duration: 'line',
  });
});
