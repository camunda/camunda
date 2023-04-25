/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {mount} from 'enzyme';

import {BPMNDiagram, BPMNDiagramProps} from './BPMNDiagram';
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer';
import Viewer from 'bpmn-js/lib/Viewer';

const props: BPMNDiagramProps = {
  mightFail: jest.fn().mockImplementation(async (data, cb) => cb(await data)),
  xml: 'diagram-xml',
  theme: 'light',
  toggleTheme: jest.fn(),
};

jest.mock('bpmn-js/lib/NavigatedViewer', () => {
  const attachSpy = jest.fn();
  return class NavigatedViewer {
    canvas: {
      resized: jest.Mock<any, any, any>;
      zoom: jest.Mock<any, any, any>;
      viewbox: jest.Mock<any, any, any>;
    };
    zoomScroll: {stepZoom: jest.Mock<any, any, any>};
    constructor() {
      this.canvas = {
        resized: jest.fn(),
        zoom: jest.fn(),
        viewbox: jest.fn().mockReturnValue({}),
      };

      this.zoomScroll = {
        stepZoom: jest.fn(),
      };
    }
    attachTo = attachSpy;
    detach = jest.fn();
    importXML = jest.fn();
    _container = {
      querySelector: () => {
        return {
          querySelector: () => {
            return {
              cloneNode: () => {
                return {
                  setAttribute: jest.fn(),
                };
              },
            };
          },
          appendChild: jest.fn(),
        };
      },
    };
    get = (prop: keyof this) => {
      return this[prop];
    };
  };
});

jest.mock('bpmn-js/lib/Viewer', () => {
  const attachSpy = jest.fn();
  return class Viewer {
    canvas: {
      resized: jest.Mock<any, any, any>;
      zoom: jest.Mock<any, any, any>;
      viewbox: jest.Mock<any, any, any>;
    };
    constructor() {
      this.canvas = {
        resized: jest.fn(),
        zoom: jest.fn(),
        viewbox: jest.fn().mockReturnValue({}),
      };
    }
    attachTo = attachSpy;
    detach = jest.fn();
    importXML = jest.fn();
    _container = {
      querySelector: () => {
        return {
          querySelector: () => {
            return {
              cloneNode: () => {
                return {
                  setAttribute: jest.fn(),
                };
              },
            };
          },
          appendChild: jest.fn(),
        };
      },
    };
    get = () => {
      return this.canvas;
    };
  };
});

beforeEach(() => {
  Object.defineProperty(global, 'ResizeObserver', {
    writable: true,
    value: jest.fn().mockImplementation(() => ({
      observe: jest.fn(() => 'Mocking works'),
      disconnect: jest.fn(),
    })),
  });
});

const diagramXml = 'some diagram XML';

it('should create a Viewer', async () => {
  const node = mount<BPMNDiagram>(<BPMNDiagram {...props} />);

  await flushPromises();

  expect(node.instance().viewer).toBeInstanceOf(NavigatedViewer);
});

it('should create a Viewer without Navigation if Navigation is disabled', async () => {
  const node = mount<BPMNDiagram>(<BPMNDiagram {...props} disableNavigation />);

  await flushPromises();

  expect(node.instance().viewer).toBeInstanceOf(Viewer);
});

it('should import the provided xml', async () => {
  const node = mount<BPMNDiagram>(<BPMNDiagram {...props} xml={diagramXml} />);
  await flushPromises();

  expect(node.instance().viewer?.importXML).toHaveBeenCalled();
  expect((node.instance().viewer?.importXML as jest.Mock).mock.calls[0][0]).toBe(diagramXml);
});

it('should not create viewer if xml is not provided', async () => {
  const node = mount<BPMNDiagram>(<BPMNDiagram {...props} xml={null} />);
  await flushPromises();

  expect(node.instance().viewer).not.toBeDefined();
});

it('should import an updated xml', async () => {
  const node = mount<BPMNDiagram>(<BPMNDiagram {...props} xml={diagramXml} />);

  node.setProps({xml: 'some other xml'});

  await flushPromises();

  expect((node.instance().viewer?.importXML as jest.Mock).mock.calls[0][0]).toBe('some other xml');
});

it('should handle rapid xml updates well', async () => {
  const node = mount<BPMNDiagram>(<BPMNDiagram {...props} xml={diagramXml} />);

  await flushPromises();
  (node.instance().viewer?.attachTo as jest.Mock).mockClear();

  node.setProps({xml: 'first update xml'});
  node.setProps({xml: 'second update xml'});

  await flushPromises();

  expect(node.instance().viewer?.attachTo).toHaveBeenCalledTimes(1);
});

it('should attach a resize observer', async () => {
  const node = mount<BPMNDiagram>(<BPMNDiagram {...props} xml={diagramXml} />);

  await flushPromises();

  expect(ResizeObserver).toHaveBeenCalledWith(node.instance().fitDiagram);
});

it('should not render children when diagram is not loaded', async () => {
  const node = mount<BPMNDiagram>(
    <BPMNDiagram {...props} xml={diagramXml}>
      <p>Additional Content</p>
    </BPMNDiagram>
  );

  await flushPromises();

  node.setState({loaded: false});

  expect(node).not.toIncludeText('Additional Content');
});

it('should render children when diagram is renderd', async () => {
  const node = mount<BPMNDiagram>(
    <BPMNDiagram {...props} xml={diagramXml}>
      <p>Additional Content</p>
    </BPMNDiagram>
  );

  await flushPromises();

  expect(node).toIncludeText('Additional Content');
});

it('should pass viewer instance to children', async () => {
  const node = mount<BPMNDiagram>(
    <BPMNDiagram {...props} xml={diagramXml}>
      <p>Additional Content</p>
    </BPMNDiagram>
  );

  await flushPromises();

  node.setState({loaded: true});

  expect(node.find('p').prop('viewer')).toBe(node.instance().viewer);
});

it('should re-use viewer instances', async () => {
  const node1 = mount<BPMNDiagram>(
    <BPMNDiagram {...props} xml={diagramXml}>
      <p>Additional Content</p>
    </BPMNDiagram>
  );

  await flushPromises();

  const viewer1 = node1.instance().viewer;
  node1.unmount();

  const node2 = mount<BPMNDiagram>(
    <BPMNDiagram {...props} xml={diagramXml}>
      <p>Additional Content</p>
    </BPMNDiagram>
  );

  await flushPromises();

  const viewer2 = node2.instance().viewer;

  expect(viewer1).toBe(viewer2);
});

it('should not re-use modeler instances', async () => {
  const node1 = mount<BPMNDiagram>(<BPMNDiagram {...props} allowModeling />);
  await flushPromises();

  const viewer1 = node1.instance().viewer;
  node1.unmount();

  const node2 = mount<BPMNDiagram>(<BPMNDiagram {...props} allowModeling />);
  await flushPromises();

  const viewer2 = node2.instance().viewer;
  expect(viewer1).not.toBe(viewer2);
});

it('should show a loading indicator while loading', () => {
  const node = mount<BPMNDiagram>(<BPMNDiagram {...props} xml={diagramXml} />);

  expect(node.find('LoadingIndicator')).toExist();
});

it('should show a loading indicator if specified by props', async () => {
  const node = mount<BPMNDiagram>(<BPMNDiagram {...props} xml={diagramXml} loading />);

  await flushPromises();

  node.setState({loaded: true});

  expect(node.find('LoadingIndicator')).toExist();
});

it('should show diagram zoom and reset controls', async () => {
  const node = mount<BPMNDiagram>(<BPMNDiagram {...props} xml={diagramXml} />);

  await flushPromises();

  node.setState({loaded: true});

  expect(node.find('ZoomControls')).toExist();
});

it('should trigger diagram zoom when zoom function is called', async () => {
  const node = mount<BPMNDiagram>(<BPMNDiagram {...props} xml={diagramXml} />);

  await flushPromises();

  node.setState({loaded: true});
  node.instance().zoom(5);

  expect(node.instance().viewer?.get<any>('zoomScroll').stepZoom).toHaveBeenCalledWith(5);
});

it('should reset the canvas zoom to viewport when fit diagram function is called', async () => {
  const node = mount<BPMNDiagram>(<BPMNDiagram {...props} xml={diagramXml} />);
  node.instance().storeContainer({clientHeight: 100} as HTMLDivElement);

  await flushPromises();

  node.setState({loaded: true});

  node.instance().fitDiagram();

  expect(node.instance().viewer?.get<any>('canvas').zoom).toHaveBeenCalledWith(
    'fit-viewport',
    'auto'
  );
});
