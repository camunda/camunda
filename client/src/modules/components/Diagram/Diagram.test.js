import React from 'react';
import {shallow} from 'enzyme';

import {mockResolvedAsyncFn, flushPromises} from 'modules/testUtils';
import {Colors} from 'modules/theme';

import ThemedDiagram from './Diagram';
import * as api from 'modules/api/diagram';
import * as Styled from './styled';
import DiagramControls from './DiagramControls';

const {WrappedComponent: Diagram} = ThemedDiagram;

// mocking
const xmlMock = '<foo />';
jest.mock('modules/api/diagram');
api.workflowXML = mockResolvedAsyncFn(xmlMock);

describe('Diagram', () => {
  const workflowId = 'some-id';

  beforeEach(() => {
    api.workflowXML.mockClear();
  });

  it('should set initial containerNode and Viewer to null', () => {
    // given
    const nodeInstance = new Diagram();

    // then
    expect(nodeInstance.Viewer).toBe(null);
    expect(nodeInstance.containerNode).toBe(null);
    expect(nodeInstance.workflowXML).toBe(null);
  });

  it('should render Diagram with controls', async () => {
    // given
    const node = shallow(<Diagram workflowId={workflowId} theme={'dark'} />);
    const nodeInstance = node.instance();
    await flushPromises();

    // then
    expect(node.find(Styled.Diagram)).toHaveLength(1);
    const DiagramCanvasNode = node.find(Styled.DiagramCanvas);
    expect(DiagramCanvasNode).toHaveLength(1);
    expect(DiagramCanvasNode.prop('innerRef')).toBe(nodeInstance.containerRef);
    const DiagramControlsNode = node.find(DiagramControls);
    expect(DiagramControlsNode).toHaveLength(1);
    expect(DiagramControlsNode.prop('handleZoomIn')).toBe(
      nodeInstance.handleZoomIn
    );
    expect(DiagramControlsNode.prop('handleZoomOut')).toBe(
      nodeInstance.handleZoomOut
    );
    expect(DiagramControlsNode.prop('handleZoomReset')).toBe(
      nodeInstance.handleZoomReset
    );
    expect(node).toMatchSnapshot();
  });

  it('should reinitiate the Viewer when theme changes', async () => {
    // given
    const node = shallow(<Diagram workflowId={workflowId} theme={'dark'} />);
    const nodeInstance = node.instance();
    const initViewerSpy = jest.spyOn(nodeInstance, 'initViewer');
    await flushPromises();
    initViewerSpy.mockClear();

    // when
    node.setProps({theme: 'light'});

    // then
    expect(initViewerSpy).toHaveBeenCalledTimes(1);
  });

  describe('componentDidMount', async () => {
    it('should get xml from api and initiate Viewer', async () => {
      // given
      const node = shallow(<Diagram workflowId={workflowId} theme={'dark'} />);
      const nodeInstance = node.instance();
      const initViewerSpy = jest.spyOn(nodeInstance, 'initViewer');
      await flushPromises();

      // then
      expect(api.workflowXML).toBeCalledWith(workflowId);
      expect(nodeInstance.workflowXML).toBe(xmlMock);
      expect(initViewerSpy).toHaveBeenCalledTimes(1);
    });
  });

  describe('initViewer', () => {
    it('should detach Viewer if it exists', async () => {
      // given
      const node = shallow(<Diagram workflowId={workflowId} theme={'dark'} />);
      const nodeInstance = node.instance();
      await flushPromises();

      // when
      const {detach} = nodeInstance.Viewer;
      nodeInstance.initViewer();

      // then
      expect(detach).toHaveBeenCalledTimes(1);
    });

    it('should initiate dark BPMNViewer if theme is dark', async () => {
      // given
      const node = shallow(<Diagram workflowId={workflowId} theme={'dark'} />);
      const nodeInstance = node.instance();
      await flushPromises();

      //then
      const {container, bpmnRenderer} = nodeInstance.Viewer;
      expect(container).toBe(nodeInstance.containerNode);
      expect(bpmnRenderer).toEqual({
        defaultFillColor: Colors.uiDark02,
        defaultStrokeColor: Colors.darkDiagram
      });
    });

    it('should initiate light BPMNViewer if theme is light', async () => {
      // given
      const node = shallow(<Diagram workflowId={workflowId} theme={'light'} />);
      await flushPromises();
      const nodeInstance = node.instance();

      //then
      const {container, bpmnRenderer} = nodeInstance.Viewer;
      expect(container).toBe(nodeInstance.containerNode);
      expect(bpmnRenderer).toEqual({
        defaultFillColor: Colors.uiLight04,
        defaultStrokeColor: Colors.uiLight06
      });
    });

    it('should import the xml in the Viewer and reset zoom', async () => {
      // given
      const node = shallow(<Diagram workflowId={workflowId} theme={'light'} />);
      const nodeInstance = node.instance();
      const handleZoomResetSpy = jest.spyOn(nodeInstance, 'handleZoomReset');
      await flushPromises();

      // then
      const args = nodeInstance.Viewer.importXML.mock.calls[0];
      expect(args[0]).toBe(nodeInstance.workflowXML);
      expect(typeof args[1]).toBe('function');
      expect(handleZoomResetSpy).toHaveBeenCalledTimes(1);
    });
  });

  describe('containerRef', () => {
    it('should set containerNode to provided node', async () => {
      // given
      const node = shallow(<Diagram workflowId={workflowId} theme={'dark'} />);
      await flushPromises();
      const nodeInstance = node.instance();
      const someNode = 'some/node';

      // when
      nodeInstance.containerRef(someNode);

      // then
      expect(nodeInstance.containerNode).toBe(someNode);
    });
  });

  describe('handleZoom', () => {
    it('should call stepZoom with provided step', async () => {
      // given
      const node = shallow(<Diagram workflowId={workflowId} theme={'dark'} />);
      const nodeInstance = node.instance();
      await flushPromises();
      const step = 1;
      const stepZoomMock = nodeInstance.Viewer.zoomScroll.stepZoom;

      // when
      nodeInstance.handleZoom(step);

      // then
      expect(stepZoomMock).toBeCalledWith(step);
    });
  });

  describe('handleZoomIn', () => {
    it('should call handleZoom with 0.1 step', async () => {
      // given
      const node = shallow(<Diagram workflowId={workflowId} theme={'dark'} />);
      const nodeInstance = node.instance();
      await flushPromises();
      const handleZoomSpy = jest.spyOn(nodeInstance, 'handleZoom');

      // when
      nodeInstance.handleZoomIn();

      // then
      expect(handleZoomSpy).toBeCalledWith(0.1);
    });
  });

  describe('handleZoomOut', () => {
    it('should call handleZoom with -0.1 step', async () => {
      // given
      const node = shallow(<Diagram workflowId={workflowId} theme={'dark'} />);
      const nodeInstance = node.instance();
      await flushPromises();
      const handleZoomSpy = jest.spyOn(nodeInstance, 'handleZoom');

      // when
      nodeInstance.handleZoomOut();

      // then
      expect(handleZoomSpy).toBeCalledWith(-0.1);
    });
  });

  describe('handleZoomReset', () => {
    it('should reset zoom in canvas', async () => {
      // given
      const node = shallow(<Diagram workflowId={workflowId} theme={'dark'} />);
      const nodeInstance = node.instance();
      await flushPromises();
      const zoom = nodeInstance.Viewer.canvas.zoom;

      // when
      nodeInstance.handleZoomReset();

      // then
      expect(zoom).toBeCalledWith('fit-viewport', 'auto');
    });
  });
});
