import React from 'react';
import {mount} from 'enzyme';

import Diagram from './Diagram';
import Viewer from 'bpmn-js/lib/NavigatedViewer';

jest.mock('bpmn-js/lib/NavigatedViewer', () => {return class Viewer {
  constructor() {
    this.canvas = {
      resized: jest.fn(),
      zoom: jest.fn()
    }
  }
  attachTo = jest.fn()
  importXML = jest.fn((xml, cb) => cb())
  get = () => {return this.canvas}
}});

const diagramXml = 'some diagram XML';

it('should create a Viewer', () => {
  const node = mount(<Diagram />);

  expect(node.instance().viewer).toBeInstanceOf(Viewer);
});

it('should import the provided xml', () => {
  const node = mount(<Diagram xml={diagramXml} />);

  expect(node.instance().viewer.importXML).toHaveBeenCalled();
  expect(node.instance().viewer.importXML.mock.calls[0][0]).toBe(diagramXml);
});

it('should resize the diagram to fit the container initially', () => {
  const node = mount(<Diagram xml={diagramXml} />);

  expect(node.instance().viewer.canvas.resized).toHaveBeenCalled();
});

it('should not render children when diagram is not loaded', () => {
  const node = mount(<Diagram xml={diagramXml}>
    <p>Additional Content</p>
  </Diagram>);

  node.setState({loaded: false});

  expect(node).not.toIncludeText('Additional Content');
});

it('should render children when diagram is renderd', () => {
  const node = mount(<Diagram xml={diagramXml}>
    <p>Additional Content</p>
  </Diagram>);

  expect(node).toIncludeText('Additional Content');
});

it('should pass viewer instance to children', () => {
  const node = mount(<Diagram xml={diagramXml}>
    <p>Additional Content</p>
  </Diagram>);

  expect(node.find('p').prop('viewer')).toBe(node.instance().viewer);
});
