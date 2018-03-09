import React from 'react';
import {mount} from 'enzyme';

import Heatmap from './Heatmap';
import HeatmapOverlay from './HeatmapOverlay';
import {calculateTargetValueHeat, convertToMilliseconds} from './service';
import {formatters} from 'services';

jest.mock('components', () => {
  return {
    BPMNDiagram: props => (
      <div id="diagram">
        Diagram {props.children} {props.xml}
      </div>
    ),
    TargetValueBadge: () => <div>TargetValuesBadge</div>
  };
});
jest.mock('./HeatmapOverlay', () => props => <div>HeatmapOverlay</div>);

jest.mock('./service', () => {
  return {
    calculateTargetValueHeat: jest.fn(),
    convertToMilliseconds: jest.fn()
  };
});

jest.mock('services', () => {
  const durationFct = jest.fn();
  return {
    formatters: {duration: durationFct}
  };
});

const diagramXml = 'some diagram XML';
const data = {a: 1, b: 2};

it('should load the process definition xml', () => {
  const node = mount(<Heatmap data={data} xml={diagramXml} />);

  expect(node).toIncludeText('some diagram XML');
});

it('should load an updated process definition xml', () => {
  const node = mount(<Heatmap data={data} xml={diagramXml} />);

  node.setProps({xml: 'another xml'});

  expect(node).toIncludeText('another xml');
});

it('should display a loading indication while loading', () => {
  const node = mount(<Heatmap data={data} />);

  expect(node.find('.heatmap-loading-indicator')).toBePresent();
});

it('should display an error message if visualization is incompatible with data', () => {
  const node = mount(<Heatmap data="123" errorMessage="Error" />);

  expect(node).toIncludeText('Error');
});

it('should display a diagram', () => {
  const node = mount(<Heatmap data={data} xml={diagramXml} />);

  expect(node).toIncludeText('Diagram');
});

it('should display a heatmap overlay', () => {
  const node = mount(<Heatmap data={data} xml={diagramXml} />);

  expect(node).toIncludeText('HeatmapOverlay');
});

it('should convert the data to target value heat when target value mode is active', () => {
  const targetValue = {
    active: true,
    values: 'some values'
  };

  mount(<Heatmap data={data} xml={diagramXml} targetValue={targetValue} />);

  expect(calculateTargetValueHeat).toHaveBeenCalledWith(data, targetValue.values);
});

it('should show a tooltip with information about actual and target value', () => {
  const targetValue = {
    active: true,
    values: {
      b: {value: 1, unit: 'millis'}
    }
  };

  calculateTargetValueHeat.mockReturnValue({b: 1});
  formatters.duration.mockReturnValueOnce('1ms').mockReturnValueOnce('2ms');
  convertToMilliseconds.mockReturnValue(1);

  const node = mount(<Heatmap data={data} xml={diagramXml} targetValue={targetValue} />);

  const tooltip = node
    .find(HeatmapOverlay)
    .props()
    .formatter('', 'b');

  expect(tooltip.textContent).toContain('target duration: 1ms'.replace(/ /g, '\u00A0'));
  expect(tooltip.textContent).toContain('actual duration: 2ms'.replace(/ /g, '\u00A0'));
  expect(tooltip.textContent).toContain('200% of the target value'.replace(/ /g, '\u00A0'));
});

it('should inform if the actual value is less than 1% of the target value', () => {
  const targetValue = {
    active: true,
    values: {
      b: {value: 10000, unit: 'millis'}
    }
  };

  calculateTargetValueHeat.mockReturnValue({b: 10000});
  formatters.duration.mockReturnValueOnce('10000ms').mockReturnValueOnce('1ms');
  convertToMilliseconds.mockReturnValue(10000);

  const node = mount(<Heatmap data={data} xml={diagramXml} targetValue={targetValue} />);

  const tooltip = node
    .find(HeatmapOverlay)
    .props()
    .formatter('', 'b');

  expect(tooltip.textContent).toContain('target duration: 10000ms'.replace(/ /g, '\u00A0'));
  expect(tooltip.textContent).toContain('actual duration: 1ms'.replace(/ /g, '\u00A0'));
  expect(tooltip.textContent).toContain('< 1% of the target value'.replace(/ /g, '\u00A0'));
});

it('should show a tooltip with information if no actual value is available', () => {
  const targetValue = {
    active: true,
    values: {
      b: {value: 1, unit: 'millis'}
    }
  };

  calculateTargetValueHeat.mockReturnValue({b: undefined});
  formatters.duration.mockReturnValueOnce('1ms');
  convertToMilliseconds.mockReturnValue(1);

  const node = mount(<Heatmap data={{}} xml={diagramXml} targetValue={targetValue} />);

  const tooltip = node
    .find(HeatmapOverlay)
    .props()
    .formatter('', 'b');

  expect(tooltip.textContent).toContain('target duration: 1ms'.replace(/ /g, '\u00A0'));
  expect(tooltip.textContent).toContain('No actual value available.'.replace(/ /g, '\u00A0'));
  expect(tooltip.textContent).toContain(
    'Cannot compare target and actual value'.replace(/ /g, '\u00A0')
  );
});

it('should not display an error message if data is valid', () => {
  const node = mount(<Heatmap data={data} errorMessage="Error" />);

  expect(node).not.toIncludeText('Error');
});
