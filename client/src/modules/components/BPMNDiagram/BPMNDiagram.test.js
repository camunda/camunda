/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount, shallow} from 'enzyme';

import ThemedBPMNDiagram from './BPMNDiagram';
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer';
import Viewer from 'bpmn-js/lib/Viewer';

const {WrappedComponent: BPMNDiagram} = ThemedBPMNDiagram;

// since jest does not offer an out of the box way to flush promises:
// https://github.com/facebook/jest/issues/2157
const flushPromises = () => new Promise(resolve => setImmediate(resolve));

jest.mock('bpmn-js/lib/NavigatedViewer', () => {
  return class NavigatedViewer {
    constructor() {
      this.canvas = {
        resized: jest.fn(),
        zoom: jest.fn()
      };

      this.zoomScroll = {
        stepZoom: jest.fn()
      };
    }
    attachTo = jest.fn();
    detach = jest.fn();
    importXML = jest.fn((xml, cb) => cb());
    _container = {
      querySelector: () => {
        return {
          querySelector: () => {
            return {
              cloneNode: () => {
                return {
                  setAttribute: jest.fn()
                };
              }
            };
          },
          appendChild: jest.fn()
        };
      }
    };
    get = prop => {
      return this[prop];
    };
  };
});

jest.mock('bpmn-js/lib/Viewer', () => {
  return class Viewer {
    constructor() {
      this.canvas = {
        resized: jest.fn(),
        zoom: jest.fn()
      };
    }
    attachTo = jest.fn();
    detach = jest.fn();
    importXML = jest.fn((xml, cb) => cb());
    _container = {
      querySelector: () => {
        return {
          querySelector: () => {
            return {
              cloneNode: () => {
                return {
                  setAttribute: jest.fn()
                };
              }
            };
          },
          appendChild: jest.fn()
        };
      }
    };
    get = () => {
      return this.canvas;
    };
  };
});

jest.mock('components', () => {
  return {
    LoadingIndicator: () => <div>LoadingIndicator</div>,
    Button: props => <button {...props}>props.children</button>,
    Icon: () => <span className="icon" />
  };
});

const diagramXml = 'some diagram XML';

it('should create a Viewer', async () => {
  const node = mount(shallow(<BPMNDiagram />).get(0));

  await flushPromises();

  expect(node.instance().viewer).toBeInstanceOf(NavigatedViewer);
});

it('should create a Viewer without Navigation if Navigation is disabled', async () => {
  const node = mount(shallow(<BPMNDiagram disableNavigation />).get(0));

  await flushPromises();

  expect(node.instance().viewer).toBeInstanceOf(Viewer);
});

it('should import the provided xml', async () => {
  const node = mount(shallow(<BPMNDiagram xml={diagramXml} />).get(0));

  await flushPromises();

  expect(node.instance().viewer.importXML).toHaveBeenCalled();
  expect(node.instance().viewer.importXML.mock.calls[0][0]).toBe(diagramXml);
});

it('should import an updated xml', async () => {
  const node = mount(shallow(<BPMNDiagram xml={diagramXml} />).get(0));

  node.setProps({xml: 'some other xml'});

  await flushPromises();

  expect(node.instance().viewer.importXML.mock.calls[0][0]).toBe('some other xml');
});

it('should resize the diagram to fit the container initially', async () => {
  const node = mount(shallow(<BPMNDiagram xml={diagramXml} />).get(0));

  await flushPromises();

  expect(node.instance().viewer.canvas.resized).toHaveBeenCalled();
});

it('should not render children when diagram is not loaded', async () => {
  const node = mount(
    shallow(
      <BPMNDiagram xml={diagramXml}>
        <p>Additional Content</p>
      </BPMNDiagram>
    ).get(0)
  );

  await flushPromises();

  node.setState({loaded: false});

  expect(node).not.toIncludeText('Additional Content');
});

it('should render children when diagram is renderd', async () => {
  const node = mount(
    shallow(
      <BPMNDiagram xml={diagramXml}>
        <p>Additional Content</p>
      </BPMNDiagram>
    ).get(0)
  );

  await flushPromises();

  expect(node).toIncludeText('Additional Content');
});

it('should pass viewer instance to children', async () => {
  const node = mount(
    shallow(
      <BPMNDiagram xml={diagramXml}>
        <p>Additional Content</p>
      </BPMNDiagram>
    ).get(0)
  );

  await flushPromises();

  node.setState({loaded: true});

  expect(node.find('p').prop('viewer')).toBe(node.instance().viewer);
});

it('should register an Mutation Observer if its on a Dashboard', () => {
  mount(
    shallow(
      <div className="DashboardObject">
        <BPMNDiagram xml={diagramXml} />
      </div>
    ).get(0)
  );

  // we can maybe have some meaningful assertion here once jsdom supports MutationObservers:
  // https://github.com/tmpvar/jsdom/issues/639
});

it('should re-use viewer instances', async () => {
  const node1 = mount(
    shallow(
      <BPMNDiagram xml={diagramXml}>
        <p>Additional Content</p>
      </BPMNDiagram>
    ).get(0)
  );

  await flushPromises();

  const viewer1 = node1.instance().viewer;
  node1.unmount();

  const node2 = mount(
    shallow(
      <BPMNDiagram xml={diagramXml}>
        <p>Additional Content</p>
      </BPMNDiagram>
    ).get(0)
  );

  await flushPromises();

  const viewer2 = node2.instance().viewer;

  expect(viewer1).toBe(viewer2);
});

it('should show a loading indicator while loading', () => {
  const node = mount(shallow(<BPMNDiagram xml={diagramXml} />).get(0));

  expect(node).toIncludeText('LoadingIndicator');
});

it('should show diagram zoom and reset controls', async () => {
  const node = mount(shallow(<BPMNDiagram xml={diagramXml} />).get(0));

  await flushPromises();

  node.setState({loaded: true});

  expect(node.find('ZoomControls')).toBePresent();
});

it('should trigger diagram zoom when zoom function is called', async () => {
  const node = mount(shallow(<BPMNDiagram xml={diagramXml} />).get(0));

  await flushPromises();

  node.setState({loaded: true});
  node.instance().zoom(5);

  expect(node.instance().viewer.zoomScroll.stepZoom).toHaveBeenCalledWith(5);
});

it('should reset the canvas zoom to viewport when fit diagram function is called', async () => {
  const node = mount(shallow(<BPMNDiagram xml={diagramXml} />).get(0));

  await flushPromises();

  node.setState({loaded: true});

  node.instance().fitDiagram();

  expect(node.instance().viewer.canvas.zoom).toHaveBeenCalledWith('fit-viewport', 'auto');
});
