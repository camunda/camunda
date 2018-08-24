import React from 'react';
import {shallow} from 'enzyme';

import {mockResolvedAsyncFn, flushPromises} from 'modules/testUtils';
import {Colors} from 'modules/theme';
import * as api from 'modules/api/diagram/diagram';

import ThemedDiagram from './Diagram';
import DiagramControls from './DiagramControls';
import * as service from './service';
import * as Styled from './styled';

const {WrappedComponent: Diagram} = ThemedDiagram;

// mocking api
const xmlMock = '<foo />';
api.fetchWorkflowXML = mockResolvedAsyncFn(xmlMock);

// mocking service
const flowNodesDetails = {};
service.getFlowNodesDetails = jest.fn(() => flowNodesDetails);

describe('Diagram', () => {
  const workflowId = 'some-id';

  beforeEach(() => {
    api.fetchWorkflowXML.mockClear();
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

  it('should fetch new workflow and reinitiate the Viewer when workflowid changes', async () => {
    // given
    const node = shallow(<Diagram workflowId={'foo'} theme={'dark'} />);
    const nodeInstance = node.instance();
    const initViewerSpy = jest.spyOn(nodeInstance, 'initViewer');
    await flushPromises();
    initViewerSpy.mockClear();
    api.fetchWorkflowXML.mockClear();

    // when
    node.setProps({workflowId: 'bar'});
    await flushPromises();

    // then
    expect(api.fetchWorkflowXML).toHaveBeenCalledTimes(1);
    expect(initViewerSpy).toHaveBeenCalledTimes(1);
  });

  it('should call handleSelectedFlowNode if only selectedFlowNode changed', async () => {
    // given
    const node = shallow(
      <Diagram workflowId={workflowId} theme={'dark'} selectedFlowNode="foo" />
    );
    await flushPromises();
    const handleSelectedFlowNodeSpy = jest.spyOn(
      node.instance(),
      'handleSelectedFlowNode'
    );

    // when
    node.setProps({selectedFlowNode: 'bar'});
    await flushPromises();

    // then
    expect(handleSelectedFlowNodeSpy).toBeCalledWith('bar', 'foo');
  });

  describe('componentDidMount', async () => {
    it('should get xml from api and initiate Viewer', async () => {
      // given
      const node = shallow(<Diagram workflowId={workflowId} theme={'dark'} />);
      const nodeInstance = node.instance();
      const initViewerSpy = jest.spyOn(nodeInstance, 'initViewer');
      await flushPromises();

      // then
      expect(api.fetchWorkflowXML).toBeCalledWith(workflowId);
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
      const onFlowNodesDetailsReady = jest.fn();
      const node = shallow(
        <Diagram
          workflowId={workflowId}
          theme={'light'}
          onFlowNodesDetailsReady={onFlowNodesDetailsReady}
        />
      );
      const nodeInstance = node.instance();
      const handleZoomResetSpy = jest.spyOn(nodeInstance, 'handleZoomReset');
      await flushPromises();

      // then
      const args = nodeInstance.Viewer.importXML.mock.calls[0];
      expect(args[0]).toBe(nodeInstance.workflowXML);
      expect(typeof args[1]).toBe('function');
      expect(service.getFlowNodesDetails).toBeCalledWith(
        nodeInstance.Viewer.elementRegistry
      );
      expect(onFlowNodesDetailsReady).toBeCalledWith(flowNodesDetails);
      expect(handleZoomResetSpy).toHaveBeenCalledTimes(1);
    });

    it('should add a selectable overlay to each selectable flowNode', async () => {
      // given
      const selectableFlowNodes = ['foo', 'bar'];
      const node = shallow(
        <Diagram
          workflowId={workflowId}
          theme={'light'}
          selectableFlowNodes={selectableFlowNodes}
        />
      );
      const addOverlaySpy = jest.spyOn(node.instance(), 'addOverlay');
      await flushPromises();

      // then
      expect(addOverlaySpy).toBeCalledWith('foo', 'op-selectable');
      expect(addOverlaySpy).toBeCalledWith('bar', 'op-selectable');
    });

    it('should call handleSelectedFlowNode if selectedFlowNode is provided', async () => {
      // given
      const node = shallow(
        <Diagram
          workflowId={workflowId}
          theme={'light'}
          selectedFlowNode={'foo'}
        />
      );
      const handleSelectedFlowNodeSpy = jest.spyOn(
        node.instance(),
        'handleSelectedFlowNode'
      );
      await flushPromises();

      // then
      expect(handleSelectedFlowNodeSpy).toBeCalledWith('foo');
    });

    it('should add event listeners if both selectableFlowNodes and onFlowNodeSelected are provided', async () => {
      // given
      const selectableFlowNodes = ['foo', 'bar'];
      const node = shallow(
        <Diagram
          workflowId={workflowId}
          theme={'light'}
          selectableFlowNodes={selectableFlowNodes}
          onFlowNodeSelected={jest.fn()}
        />
      );
      await flushPromises();

      // then
      expect(node.instance().Viewer.eventBus.on).toBeCalledWith(
        'element.click',
        node.instance().handleElementClick
      );
    });
  });

  describe('handleDiagramLoad', () => {});

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

  describe('addOverlay', () => {
    it('should add marker to the canvas', async () => {
      // given
      const node = shallow(<Diagram workflowId={workflowId} theme={'dark'} />);
      await flushPromises();

      // when
      node.instance().addOverlay('foo', 'fooClassName');

      // then
      expect(node.instance().Viewer.canvas.addMarker).toBeCalledWith(
        'foo',
        'fooClassName'
      );
    });
  });

  describe('removeOverlay', () => {
    it('should remove marker from the canvas', async () => {
      // given
      const node = shallow(<Diagram workflowId={workflowId} theme={'dark'} />);
      await flushPromises();

      // when
      node.instance().removeOverlay('foo', 'fooClassName');

      // then
      expect(node.instance().Viewer.canvas.removeMarker).toBeCalledWith(
        'foo',
        'fooClassName'
      );
    });
  });

  describe('handleSelectedFlowNode', () => {
    it('should remove previous node and add the new one', async () => {
      // given
      const node = shallow(<Diagram workflowId={workflowId} theme={'dark'} />);
      await flushPromises();
      const removeOverlaySpy = jest.spyOn(node.instance(), 'removeOverlay');
      const addOverlaySpy = jest.spyOn(node.instance(), 'addOverlay');

      // when
      node.instance().handleSelectedFlowNode('foo', 'bar');

      // then
      expect(removeOverlaySpy).toBeCalledWith('bar', 'op-selected');
      expect(addOverlaySpy).toBeCalledWith('foo', 'op-selected');
    });

    it("should not remove previous node if it's not provided", async () => {
      // given
      const node = shallow(<Diagram workflowId={workflowId} theme={'dark'} />);
      await flushPromises();
      const removeOverlaySpy = jest.spyOn(node.instance(), 'removeOverlay');
      const addOverlaySpy = jest.spyOn(node.instance(), 'addOverlay');

      // when
      node.instance().handleSelectedFlowNode('foo');

      // then
      expect(removeOverlaySpy).not.toBeCalled();
      expect(addOverlaySpy).toBeCalledWith('foo', 'op-selected');
    });

    it("should not add new node if it's not provided", async () => {
      // given
      const node = shallow(<Diagram workflowId={workflowId} theme={'dark'} />);
      await flushPromises();
      const removeOverlaySpy = jest.spyOn(node.instance(), 'removeOverlay');
      const addOverlaySpy = jest.spyOn(node.instance(), 'addOverlay');

      // when
      node.instance().handleSelectedFlowNode(null, 'bar');

      // then
      expect(removeOverlaySpy).toBeCalledWith('bar', 'op-selected');
      expect(addOverlaySpy).not.toBeCalled();
    });
  });

  describe('handleElementClick', () => {
    it('should select flownode', async () => {
      // given
      const selectableFlowNodes = ['foo', 'bar'];
      const onFlowNodeSelected = jest.fn();
      const node = shallow(
        <Diagram
          workflowId={workflowId}
          theme={'dark'}
          selectableFlowNodes={selectableFlowNodes}
          onFlowNodeSelected={onFlowNodeSelected}
        />
      );
      await flushPromises();

      // when
      node.instance().handleElementClick({element: {id: 'foo'}});

      // then
      expect(onFlowNodeSelected).toBeCalledWith('foo');
    });

    it("should not select element if it's not selectable", async () => {
      // given
      const selectableFlowNodes = ['bar'];
      const onFlowNodeSelected = jest.fn();
      const node = shallow(
        <Diagram
          workflowId={workflowId}
          theme={'dark'}
          selectableFlowNodes={selectableFlowNodes}
          onFlowNodeSelected={onFlowNodeSelected}
        />
      );
      await flushPromises();

      // when
      node.instance().handleElementClick({element: {id: 'foo'}});

      // then
      expect(onFlowNodeSelected).not.toBeCalled();
    });

    it("should deselect element if it's already selected", async () => {
      // given
      const selectableFlowNodes = ['foo', 'bar'];
      const onFlowNodeSelected = jest.fn();
      const node = shallow(
        <Diagram
          workflowId={workflowId}
          theme={'dark'}
          selectedFlowNode="foo"
          selectableFlowNodes={selectableFlowNodes}
          onFlowNodeSelected={onFlowNodeSelected}
        />
      );
      await flushPromises();

      // when
      node.instance().handleElementClick({element: {id: 'foo'}});

      // then
      expect(onFlowNodeSelected).toBeCalledWith(null);
    });
  });
});
