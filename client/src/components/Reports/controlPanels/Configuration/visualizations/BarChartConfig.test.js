/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import BarChartConfig from './BarChartConfig';

const configuration = {
  showInstanceCount: false,
  color: '#1991c8',
  hideRelativeValue: false,
  hideAbsoluteValue: false,
  xLabel: '',
  yLabel: '',
  targetValue: {active: false},
};

const barReport = {
  combined: false,
  data: {
    visualization: 'bar',
    view: {property: 'frequency'},
    groupBy: {},
    distributedBy: {type: 'none', value: null},
    configuration,
  },
};

it('it should display correct configuration for barchart', () => {
  const node = shallow(<BarChartConfig report={barReport} />);
  expect(node).toMatchSnapshot();
});

it('should not display show instance count and color picker for combined reports', () => {
  const node = shallow(
    <BarChartConfig
      report={{
        ...barReport,
        combined: true,
        result: {data: {test: {data: {view: {property: 'frequency'}}}}},
      }}
    />
  );

  expect(node.find('ShowInstanceCount')).not.toExist();
  expect(node.find('ColorPicker')).not.toExist();
});

it('should not display color picker for hyper reports (distributed by userTask/assignee/candidateGroup)', () => {
  const node = shallow(
    <BarChartConfig
      report={{
        ...barReport,
        data: {
          ...barReport.data,
          groupBy: {type: 'assignee'},
          distributedBy: {type: 'userTask', value: null},
        },
      }}
    />
  );

  expect(node.find('ColorPicker')).not.toExist();
});
