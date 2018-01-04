import React from 'react';
import {mount} from 'enzyme';

import Heatmap from './Heatmap';
import {loadProcessDefinitionXml} from './service';

jest.mock('./service', () => {return {
  loadProcessDefinitionXml: jest.fn()
}});

jest.mock('components', () => {return {
  BPMNDiagram: props => <div>Diagram {props.children}</div>
}});
jest.mock('./HeatmapOverlay', () => props => <div>HeatmapOverlay</div>);

const diagramXml = 'some diagram XML';
const data = {'a': 1, 'b': 2};

it('should load the process definition xml', () => {
  mount(<Heatmap process='a' data={data} />);

  expect(loadProcessDefinitionXml).toHaveBeenCalledWith('a');
});

it('should load an updated process definition xml', () => {
  const node = mount(<Heatmap process='a' data={data} />);

  node.setProps({process: 'b'});

  expect(loadProcessDefinitionXml).toHaveBeenCalledWith('b');
});

it('should display a loading indication while loading', () => {
  const node = mount(<Heatmap process='a' data={data} />);

  expect(node.find('.heatmap-loading-indicator')).toBePresent();
});

it('should display an error message if visualization is incompatible with data', () => {
  const node = mount(<Heatmap process='a' data='123' errorMessage='Error' />);

  expect(node).toIncludeText('Error');
});

it('should display a diagram', async () => {
  loadProcessDefinitionXml.mockReturnValue(diagramXml);

  const node = mount(<Heatmap process='a' data={data} />);

  await node.instance().load();

  expect(node).toIncludeText('Diagram');
});

it('should display a heatmap overlay', async () => {
  loadProcessDefinitionXml.mockReturnValue(diagramXml);

  const node = mount(<Heatmap process='a' data={data} />);

  await node.instance().load();

  expect(node).toIncludeText('HeatmapOverlay');
});

it('should not display an error message if data is valid', () => {
  const node = mount(<Heatmap process='a' data={data} errorMessage='Error' />);

  expect(node).not.toIncludeText('Error');
});
