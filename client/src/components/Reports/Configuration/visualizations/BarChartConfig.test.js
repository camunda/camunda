import React from 'react';
import {shallow} from 'enzyme';
import BarChartConfig from './BarChartConfig';

const barReport = {
  combined: false,
  data: {visualization: 'bar', view: {property: 'frequency'}}
};

const props = {
  report: {combined: false}
};

const configuration = {
  showInstanceCount: false,
  color: '#1991c8',
  hideRelativeValue: false,
  hideAbsoluteValue: false,
  xLabel: '',
  yLabel: '',
  targetValue: {active: false}
};

it('it should display correct configuration for barchart', () => {
  const node = shallow(<BarChartConfig {...{report: barReport, configuration}} />);
  expect(node).toMatchSnapshot();
});

it('should reset to defaults when the property changes', () => {
  expect(
    BarChartConfig.onUpdate(
      {report: {data: {view: {property: 'prev'}}}},
      {report: {data: {view: {property: 'new'}}}}
    )
  ).toEqual({
    ...BarChartConfig.defaults(props),
    targetValue: null
  });
});

it('should reset to defaults when the entity changes', () => {
  expect(
    BarChartConfig.onUpdate(
      {report: {data: {view: {entity: 'prev'}}}},
      {report: {data: {view: {entity: 'new'}}}}
    )
  ).toEqual({
    ...BarChartConfig.defaults(props),
    targetValue: null
  });
});

it('should reset to defaults when visualization type changes', () => {
  expect(
    BarChartConfig.onUpdate(
      {type: 'prev', report: {data: {view: {entity: 'test'}}}},
      {type: 'new', report: {data: {view: {entity: 'test'}}}}
    )
  ).toEqual({
    ...BarChartConfig.defaults(props),
    targetValue: null
  });
});

it('should not reset to defaults when visualization type changes from line to bar or reverse', () => {
  expect(
    BarChartConfig.onUpdate(
      {type: 'bar', report: {data: {view: {entity: 'test'}}}},
      {type: 'line', report: {data: {view: {entity: 'test'}}}}
    )
  ).toEqual(undefined);
});

it('should reset to defaults when updating combined report type', () => {
  expect(
    BarChartConfig.onUpdate(
      {
        report: {
          combined: true
        }
      },
      {
        report: {
          combined: true
        },
        type: 'bar'
      }
    )
  ).toEqual(BarChartConfig.defaults({report: {combined: true}}));
});

it('should not display show instance count and color picker for combined reports', () => {
  const node = shallow(
    <BarChartConfig
      {...{
        report: {combined: true, result: {test: {data: {view: {property: 'frequency'}}}}},
        configuration
      }}
    />
  );

  expect(node.find('ShowInstanceCount')).not.toBePresent();
  expect(node.find('ColorPicker')).not.toBePresent();
});
