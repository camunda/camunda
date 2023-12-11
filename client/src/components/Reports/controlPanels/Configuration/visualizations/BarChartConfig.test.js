/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

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

it('should not display show instance count and color picker for combined reports', () => {
  const node = shallow(<BarChartConfig report={barReport} />);

  expect(node.find('ColorPicker')).toExist();

  node.setProps({
    report: {
      ...barReport,
      combined: true,
      result: {data: {test: {data: {view: {properties: ['frequency']}}}}},
    },
  });

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

it('it should display show tooltips config for barchart', () => {
  const node = shallow(<BarChartConfig report={barReport} />);

  expect(node.find('RelativeAbsoluteSelection')).toExist();
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

  expect(node.find({labelA: 'Stacked bars'})).not.toExist();

  node.setProps({
    report: {
      ...barReport,
      data: {
        ...barReport.data,
        distributedBy: {type: 'flowNode'},
      },
    },
  });

  expect(node.find({labelA: 'Stacked bars'})).toExist();
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

  expect(node.find({label: 'Stacked bars'})).not.toExist();
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

  node.find('FormGroup').at(3).find('Toggle').simulate('toggle', true);

  expect(spy).toHaveBeenCalledWith({logScale: {$set: true}});
});

it('should show horizontalBar option for non combined bar chart reports', () => {
  const node = shallow(<BarChartConfig report={barReport} />);

  expect(node.find({labelA: 'Horizontal bars'})).toExist();

  node.setProps({
    report: {
      ...barReport,
      combined: true,
      result: {
        data: [{}],
      },
    },
  });

  expect(node.find({labelA: 'Horizontal bars'})).not.toExist();
});
