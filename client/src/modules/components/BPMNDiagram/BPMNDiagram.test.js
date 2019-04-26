/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import ThemedBPMNDiagram from './BPMNDiagram';
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer';
import Viewer from 'bpmn-js/lib/Viewer';

const {WrappedComponent: BPMNDiagramWithErrorHandling} = ThemedBPMNDiagram;
const {WrappedComponent: BPMNDiagram} = BPMNDiagramWithErrorHandling;

const props = {
  mightFail: jest.fn().mockImplementation(async (data, cb) => cb(await data))
};

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

const diagramXml = 'some diagram XML';

it('should create a Viewer', async () => {
  const node = mount(<BPMNDiagram {...props} />);

  await flushPromises();

  expect(node.instance().viewer).toBeInstanceOf(NavigatedViewer);
});

it('should create a Viewer without Navigation if Navigation is disabled', async () => {
  const node = mount(<BPMNDiagram {...props} disableNavigation />);

  await flushPromises();

  expect(node.instance().viewer).toBeInstanceOf(Viewer);
});
it('should import the provided xml', async () => {
  const node = mount(<BPMNDiagram {...props} xml={diagramXml} />);
  await flushPromises();

  expect(node.instance().viewer.importXML).toHaveBeenCalled();
  expect(node.instance().viewer.importXML.mock.calls[0][0]).toBe(diagramXml);
});

it('should import an updated xml', async () => {
  const node = mount(<BPMNDiagram xml={diagramXml} {...props} />);

  node.setProps({xml: 'some other xml'});

  await flushPromises();

  expect(node.instance().viewer.importXML.mock.calls[0][0]).toBe('some other xml');
});

it('should resize the diagram to fit the container initially', async () => {
  const node = mount(<BPMNDiagram xml={diagramXml} {...props} />);

  await flushPromises();

  expect(node.instance().viewer.canvas.resized).toHaveBeenCalled();
});

it('should not render children when diagram is not loaded', async () => {
  const node = mount(
    <BPMNDiagram {...props} xml={diagramXml}>
      <p>Additional Content</p>
    </BPMNDiagram>
  );

  await flushPromises();

  node.setState({loaded: false});

  expect(node).not.toIncludeText('Additional Content');
});

it('should render children when diagram is renderd', async () => {
  const node = mount(
    <BPMNDiagram {...props} xml={diagramXml}>
      <p>Additional Content</p>
    </BPMNDiagram>
  );

  await flushPromises();

  expect(node).toIncludeText('Additional Content');
});

it('should pass viewer instance to children', async () => {
  const node = mount(
    <BPMNDiagram {...props} xml={diagramXml}>
      <p>Additional Content</p>
    </BPMNDiagram>
  );

  await flushPromises();

  node.setState({loaded: true});

  expect(node.find('p').prop('viewer')).toBe(node.instance().viewer);
});

it('should register an Mutation Observer if its on a Dashboard', () => {
  mount(
    <div className="DashboardObject">
      <BPMNDiagram {...props} xml={diagramXml} />
    </div>
  );

  // we can maybe have some meaningful assertion here once jsdom supports MutationObservers:
  // https://github.com/tmpvar/jsdom/issues/639
});

it('should re-use viewer instances', async () => {
  const node1 = mount(
    <BPMNDiagram {...props} xml={diagramXml}>
      <p>Additional Content</p>
    </BPMNDiagram>
  );

  await flushPromises();

  const viewer1 = node1.instance().viewer;
  node1.unmount();

  const node2 = mount(
    <BPMNDiagram {...props} xml={diagramXml}>
      <p>Additional Content</p>
    </BPMNDiagram>
  );

  await flushPromises();

  const viewer2 = node2.instance().viewer;

  expect(viewer1).toBe(viewer2);
});

it('should show a loading indicator while loading', () => {
  const node = mount(<BPMNDiagram xml={diagramXml} {...props} />);

  expect(node.find('LoadingIndicator')).toBePresent();
});

it('should show diagram zoom and reset controls', async () => {
  const node = mount(<BPMNDiagram xml={diagramXml} {...props} />);

  await flushPromises();

  node.setState({loaded: true});

  expect(node.find('ZoomControls')).toBePresent();
});

it('should trigger diagram zoom when zoom function is called', async () => {
  const node = mount(<BPMNDiagram xml={diagramXml} {...props} />);

  await flushPromises();

  node.setState({loaded: true});
  node.instance().zoom(5);

  expect(node.instance().viewer.zoomScroll.stepZoom).toHaveBeenCalledWith(5);
});

it('should reset the canvas zoom to viewport when fit diagram function is called', async () => {
  const node = mount(<BPMNDiagram xml={diagramXml} {...props} />);

  await flushPromises();

  node.setState({loaded: true});

  node.instance().fitDiagram();

  expect(node.instance().viewer.canvas.zoom).toHaveBeenCalledWith('fit-viewport', 'auto');
});
