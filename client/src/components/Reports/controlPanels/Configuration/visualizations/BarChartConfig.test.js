/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
  stackedBar: false,
  logScale: false,
};

const barReport = {
  combined: false,
  data: {
    visualization: 'bar',
    view: {properties: ['frequency']},
    groupBy: {},
    distributedBy: {type: 'none', value: null},
    configuration,
  },
  result: {
    measures: [{data: []}],
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
        result: {data: {test: {data: {view: {properties: ['frequency']}}}}},
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

it('should not show target value, color picker or y axis label for multi-measure reports', () => {
  const node = shallow(
    <BarChartConfig
      report={{
        ...barReport,
        result: {
          measures: [{data: []}, {data: []}],
        },
      }}
    />
  );

  expect(node.find('ColorPicker')).not.toExist();
  expect(node.find('[placeholder="xAxis"]')).not.toExist();
  expect(node.find('ChartTargetInput')).not.toExist();
});

it('should show stacked bar option for distributed bar chart reports', () => {
  const node = shallow(<BarChartConfig report={barReport} />);

  expect(node.find('.stackedBars')).not.toExist();

  node.setProps({
    report: {
      ...barReport,
      data: {
        ...barReport.data,
        distributedBy: {type: 'flowNode'},
      },
    },
  });

  expect(node.find('.stackedBars')).toExist();
});

it('should show goal line option and not show stacking option if current visualization cannot be stacked ', () => {
  const node = shallow(<BarChartConfig report={barReport} />);

  node.setProps({
    report: {
      ...barReport,
      data: {
        ...barReport.data,
        visualization: 'line',
        distributedBy: {type: 'flowNode'},
        configuration: {...configuration, stackedBar: true},
      },
    },
  });

  expect(node.find('.stackedBars')).not.toExist();
  expect(node.find('ChartTargetInput')).toExist();
});

it('should show Color Picker for distributed by process reports that are grouped by none', () => {
  const node = shallow(
    <BarChartConfig
      report={{
        ...barReport,
        data: {
          ...barReport.data,
          groupBy: {type: 'none'},
          distributedBy: {type: 'process', value: null},
        },
      }}
    />
  );

  expect(node.find('ColorPicker')).toExist();
});

it('should set logScale to true when enabling logarithmic scale switch', () => {
  const spy = jest.fn();
  const node = shallow(<BarChartConfig report={barReport} onChange={spy} />);

  node
    .find('fieldset')
    .at(2)
    .find('Switch')
    .simulate('change', {target: {checked: true}});

  expect(spy).toHaveBeenCalledWith({logScale: {$set: true}});
});
