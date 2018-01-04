import React from 'react';
import {mount} from 'enzyme';

import BPMNDiagram from './BPMNDiagram';
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
  const node = mount(<BPMNDiagram />);

  expect(node.instance().viewer).toBeInstanceOf(Viewer);
});

it('should import the provided xml', () => {
  const node = mount(<BPMNDiagram xml={diagramXml} />);

  expect(node.instance().viewer.importXML).toHaveBeenCalled();
  expect(node.instance().viewer.importXML.mock.calls[0][0]).toBe(diagramXml);
});

it('should import an updated xml', () => {
  const node = mount(<BPMNDiagram xml={diagramXml} />);

  node.setProps({xml: 'some other xml'});
  expect(node.instance().viewer.importXML.mock.calls[1][0]).toBe('some other xml');
});

it('should resize the diagram to fit the container initially', () => {
  const node = mount(<BPMNDiagram xml={diagramXml} />);

  expect(node.instance().viewer.canvas.resized).toHaveBeenCalled();
});

it('should not render children when diagram is not loaded', () => {
  const node = mount(<BPMNDiagram xml={diagramXml}>
    <p>Additional Content</p>
  </BPMNDiagram>);

  node.setState({loaded: false});

  expect(node).not.toIncludeText('Additional Content');
});

it('should render children when diagram is renderd', () => {
  const node = mount(<BPMNDiagram xml={diagramXml}>
    <p>Additional Content</p>
  </BPMNDiagram>);

  expect(node).toIncludeText('Additional Content');
});

it('should pass viewer instance to children', () => {
  const node = mount(<BPMNDiagram xml={diagramXml}>
    <p>Additional Content</p>
  </BPMNDiagram>);

  expect(node.find('p').prop('viewer')).toBe(node.instance().viewer);
});

it('should register an Mutation Observer if its on a Dashboard', () => {
  mount(<div className="DashboardObject"><BPMNDiagram xml={diagramXml} /></div>);

  // we can maybe have some meaningful assertion here once jsdom supports MutationObservers:
  // https://github.com/tmpvar/jsdom/issues/639
});
