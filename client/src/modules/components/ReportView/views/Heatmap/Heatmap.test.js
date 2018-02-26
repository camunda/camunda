import React from 'react';
import {mount} from 'enzyme';

import Heatmap from './Heatmap';

jest.mock('components', () => {return {
  BPMNDiagram: props => <div id='diagram'>Diagram {props.children} {props.xml}</div>
}});
jest.mock('./HeatmapOverlay', () => props => <div>HeatmapOverlay</div>);

const diagramXml = 'some diagram XML';
const data = {'a': 1, 'b': 2};

it('should load the process definition xml', () => {
  const node = mount(<Heatmap  data={data} xml={diagramXml} />);

  expect(node).toIncludeText('some diagram XML');
});

it('should load an updated process definition xml', () => {
  const node = mount(<Heatmap data={data} xml={diagramXml}/>);

  node.setProps({xml: 'another xml'});

  expect(node).toIncludeText('another xml');
});

it('should display a loading indication while loading', () => {
  const node = mount(<Heatmap data={data} />);

  expect(node.find('.heatmap-loading-indicator')).toBePresent();
});

it('should display an error message if visualization is incompatible with data', () => {
  const node = mount(<Heatmap data='123' errorMessage='Error' />);

  expect(node).toIncludeText('Error');
});

it('should display a diagram', () => {
  const node = mount(<Heatmap data={data} xml={diagramXml}/>);

  expect(node).toIncludeText('Diagram');
});

it('should display a heatmap overlay', () => {
  const node = mount(<Heatmap data={data} xml={diagramXml}/>);

  expect(node).toIncludeText('HeatmapOverlay');
});

it('should not display the default heatmap overlay when targetValueMode is active', () => {
  const node = mount(<Heatmap data={data} xml={diagramXml} targetValue={{active: true}} />);

  expect(node).not.toIncludeText('HeatmapOverlay');
});

it('should not display an error message if data is valid', () => {
  const node = mount(<Heatmap data={data} errorMessage='Error' />);

  expect(node).not.toIncludeText('Error');
});
