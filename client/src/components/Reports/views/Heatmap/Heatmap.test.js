import React from 'react';
import {mount} from 'enzyme';

import Heatmap from './Heatmap';
import {loadProcessDefinitionXml} from './service';

jest.mock('./service', () => {return {
  loadProcessDefinitionXml: jest.fn()
}});

jest.mock('./Diagram', () => props => <div>Diagram {props.children}</div>);
jest.mock('./HeatmapOverlay', () => props => <div>HeatmapOverlay</div>);

const diagramXml = 'some diagram XML';
const data = {'a': 1, 'b': 2};

it('should load the process definition xml', () => {
  mount(<Heatmap process='a' data={data} />);

  expect(loadProcessDefinitionXml).toHaveBeenCalledWith('a');
});

it('should display a loading indication while loading', () => {
  const node = mount(<Heatmap process='a' data={data} />);

  expect(node).toIncludeText('loading');
});

it('should display an error message if visualization is incompatible with data', () => {
  const node = mount(<Heatmap process='a' data='123' />);

  expect(node).toIncludeText('Cannot display data');
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
