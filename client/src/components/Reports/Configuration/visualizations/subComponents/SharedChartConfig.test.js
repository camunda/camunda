import React from 'react';
import {shallow} from 'enzyme';
import SharedChartConfig from './SharedChartConfig';

const report = {combined: false, data: {view: {property: 'frequency'}}};

const configuration = {
  showInstanceCount: false,
  color: '#1991c8',
  pointMarkers: true,
  hideRelativeValue: false,
  hideAbsoluteValue: false,
  xLabel: '',
  yLabel: '',
  targetValue: {active: false}
};

it('it should match snapshot - SharedChartConfig', () => {
  const node = shallow(<SharedChartConfig {...{report, configuration}} />);
  expect(node).toMatchSnapshot();
});

it('it should hide ColorPicker for combined report configuration', () => {
  const node = shallow(
    <SharedChartConfig
      {...{
        report: {
          combined: true,
          result: {
            test1: {data: {view: {property: 'frequency'}}}
          }
        },
        configuration
      }}
    />
  );

  expect(node.find('ColorPicker')).not.toBePresent();
});

it('it should hide tooltip options when specified in props', () => {
  const node = shallow(
    <SharedChartConfig
      {...{
        report,
        configuration,
        hideTooltipOptions: true
      }}
    />
  );

  expect(node.find('RelativeAbsoluteSelection')).not.toBePresent();
});

it('should invok onchange when changing xlabel/ylabel input on bar chart', () => {
  const spy = jest.fn();
  const node = shallow(<SharedChartConfig {...{report, configuration}} onChange={spy} />);

  node
    .find('LabeledInput')
    .first()
    .simulate('change', {target: {value: 'testLabel'}});

  expect(spy).toHaveBeenCalledWith('xLabel', 'testLabel');
});

it('should invok onchange when enabling target value switch', () => {
  const spy = jest.fn();
  const node = shallow(<SharedChartConfig {...{report, configuration}} onChange={spy} />);

  node.find('.goalLine Switch').simulate('change', {target: {checked: true}});

  expect(spy).toHaveBeenCalledWith('targetValue', {
    active: true,
    values: {dateFormat: '', isBelow: false, target: ''}
  });
});
