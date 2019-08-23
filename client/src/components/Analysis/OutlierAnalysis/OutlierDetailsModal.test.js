/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import Chart from 'chart.js';
import OutlierDetailsModal from './OutlierDetailsModal';

jest.mock('chart.js');

const selectedNode = {
  name: 'test',
  higherOutlier: {
    count: 4,
    relation: 1.1
  },
  data: [{key: '5', value: '3', outlier: true}, {key: '1', value: '20', outlier: false}]
};

it('should render a modal', () => {
  const node = shallow(<OutlierDetailsModal selectedNode={selectedNode} />);

  expect(node).toMatchSnapshot();
});

it('should construct a bar Chart with the noda data', () => {
  shallow(<OutlierDetailsModal selectedNode={selectedNode} />);

  expect(Chart).toHaveBeenCalled();
  expect(Chart.mock.calls[0][1].type).toBe('bar');
  expect(Chart.mock.calls[0][1].data).toMatchSnapshot();
});

it('should create correct chart options', () => {
  shallow(<OutlierDetailsModal selectedNode={selectedNode} />);

  expect(Chart.mock.calls[0][1].options).toMatchSnapshot();
});
