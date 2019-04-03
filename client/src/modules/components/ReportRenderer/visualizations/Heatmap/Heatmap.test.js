/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import Heatmap from './Heatmap';
import HeatmapOverlay from './HeatmapOverlay';
import {calculateTargetValueHeat} from './service';
import {formatters} from 'services';

const {convertToMilliseconds} = formatters;

jest.mock('components', () => {
  return {
    BPMNDiagram: props => (
      <div id="diagram">
        Diagram {props.children} {props.xml}
      </div>
    ),
    TargetValueBadge: () => <div>TargetValuesBadge</div>,
    LoadingIndicator: props => (
      <div {...props} className="sk-circle">
        Loading...
      </div>
    )
  };
});

jest.mock('./service', () => {
  return {
    calculateTargetValueHeat: jest.fn()
  };
});

jest.mock('services', () => {
  const durationFct = jest.fn();
  return {
    formatters: {duration: durationFct, convertToMilliseconds: jest.fn()},
    isDurationReport: jest.fn().mockReturnValue(false),
    getTooltipText: jest.fn()
  };
});

const report = {
  reportType: 'process',
  combined: false,
  data: {
    configuration: {
      xml: 'some diagram XML'
    },
    view: {
      property: 'frequency'
    },
    visualization: 'heat'
  },
  result: {a: 1, b: 2},
  processInstanceCount: 5
};

it('should load the process definition xml', () => {
  const node = shallow(<Heatmap report={report} />);

  expect(node.find('BPMNDiagram').props().xml).toBe('some diagram XML');
});

it('should load an updated process definition xml', () => {
  const node = shallow(<Heatmap report={report} />);

  node.setProps({report: {...report, data: {...report.data, configuration: {xml: 'another xml'}}}});

  expect(node.find('BPMNDiagram').props().xml).toBe('another xml');
});

it('should display a loading indication while loading', () => {
  const node = shallow(
    <Heatmap report={{...report, data: {...report.data, configuration: {xml: null}}}} />
  );

  expect(node.find('LoadingIndicator')).toBePresent();
});

it('should display an error message if visualization is incompatible with data', () => {
  const node = shallow(<Heatmap report={{...report, result: 1234}} errorMessage="Error" />);

  expect(node).toIncludeText('Error');
});

it('should display a diagram', () => {
  const node = shallow(<Heatmap report={report} />);

  expect(node).toIncludeText('Diagram');
});

it('should display a heatmap overlay', () => {
  const node = shallow(<Heatmap report={report} />);

  expect(
    node
      .find('BPMNDiagram')
      .children()
      .find('HeatmapOverlay')
  ).toBePresent();
});

it('should convert the data to target value heat when target value mode is active', () => {
  const heatmapTargetValue = {
    active: true,
    values: 'some values'
  };

  shallow(
    <Heatmap
      report={{...report, data: {...report.data, configuration: {xml: 'test', heatmapTargetValue}}}}
    />
  );

  expect(calculateTargetValueHeat).toHaveBeenCalledWith(report.result, heatmapTargetValue.values);
});

it('should show a tooltip with information about actual and target value', () => {
  const heatmapTargetValue = {
    active: true,
    values: {
      b: {value: 1, unit: 'millis'}
    }
  };

  calculateTargetValueHeat.mockReturnValue({b: 1});
  formatters.duration.mockReturnValueOnce('1ms').mockReturnValueOnce('2ms');
  convertToMilliseconds.mockReturnValue(1);

  const node = shallow(
    <Heatmap
      report={{...report, data: {...report.data, configuration: {xml: 'test', heatmapTargetValue}}}}
    />
  );

  const tooltip = node
    .find(HeatmapOverlay)
    .props()
    .formatter('', 'b');

  expect(tooltip.textContent).toContain('target duration: 1ms'.replace(/ /g, '\u00A0'));
  expect(tooltip.textContent).toContain('actual duration: 2ms'.replace(/ /g, '\u00A0'));
  expect(tooltip.textContent).toContain('200% of the target value'.replace(/ /g, '\u00A0'));
});

it('should inform if the actual value is less than 1% of the target value', () => {
  const heatmapTargetValue = {
    active: true,
    values: {
      b: {value: 10000, unit: 'millis'}
    }
  };

  calculateTargetValueHeat.mockReturnValue({b: 10000});
  formatters.duration.mockReturnValueOnce('10000ms').mockReturnValueOnce('1ms');
  convertToMilliseconds.mockReturnValue(10000);

  const node = shallow(
    <Heatmap
      report={{...report, data: {...report.data, configuration: {xml: 'test', heatmapTargetValue}}}}
    />
  );

  const tooltip = node
    .find(HeatmapOverlay)
    .props()
    .formatter('', 'b');

  expect(tooltip.textContent).toContain('target duration: 10000ms'.replace(/ /g, '\u00A0'));
  expect(tooltip.textContent).toContain('actual duration: 1ms'.replace(/ /g, '\u00A0'));
  expect(tooltip.textContent).toContain('< 1% of the target value'.replace(/ /g, '\u00A0'));
});

it('should show a tooltip with information if no actual value is available', () => {
  const heatmapTargetValue = {
    active: true,
    values: {
      b: {value: 1, unit: 'millis'}
    }
  };

  calculateTargetValueHeat.mockReturnValue({b: undefined});
  formatters.duration.mockReturnValueOnce('1ms');
  convertToMilliseconds.mockReturnValue(1);

  const node = shallow(
    <Heatmap
      report={{
        ...report,
        result: {},
        data: {...report.data, configuration: {xml: 'test', heatmapTargetValue}}
      }}
    />
  );

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
  const node = shallow(<Heatmap report={report} errorMessage="Error" />);

  expect(node).not.toIncludeText('Error');
});
