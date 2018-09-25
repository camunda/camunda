import React from 'react';
import {shallow} from 'enzyme';

import {mockResolvedAsyncFn, flushPromises} from 'modules/testUtils';
import {Colors} from 'modules/theme';
import * as api from 'modules/api/diagram/diagram';
import {
  ACTIVITY_STATE,
  FLOW_NODE_STATE_OVERLAY_ID,
  ACTIVE_STATISTICS_OVERLAY_ID,
  INCIDENTS_STATISTICS_OVERLAY_ID,
  CANCELED_STATISTICS_OVERLAY_ID,
  COMPLETED_STATISTICS_OVERLAY_ID
} from 'modules/constants';
import incidentIcon from 'modules/components/Icon/diagram-badge-single-instance-incident.svg';
import activeIcon from 'modules/components/Icon/diagram-badge-single-instance-active.svg';
import completedLightIcon from 'modules/components/Icon/diagram-badge-single-instance-completed-light.svg';
import completedDarkIcon from 'modules/components/Icon/diagram-badge-single-instance-completed-dark.svg';
import canceledLightIcon from 'modules/components/Icon/diagram-badge-single-instance-canceled-light.svg';
import canceledDarkIcon from 'modules/components/Icon/diagram-badge-single-instance-canceled-dark.svg';

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
    expect(nodeInstance.state.isViewerLoaded).toBe(false);
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
    expect(node.state().isViewerLoaded).toBe(true);
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

  it('should not call handleSelectedFlowNode if the Viewer is not loaded', async () => {
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
    // the viewer is detached
    node.instance().detachViewer();
    node.update();

    node.setProps({selectedFlowNode: 'bar'});
    await flushPromises();

    // then
    expect(handleSelectedFlowNodeSpy).toHaveBeenCalledTimes(0);
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

    it('should add a selectable marker to each selectable flowNode', async () => {
      // given
      const selectableFlowNodes = ['foo', 'bar'];
      const node = shallow(
        <Diagram
          workflowId={workflowId}
          theme={'light'}
          selectableFlowNodes={selectableFlowNodes}
        />
      );
      const addMarkerSpy = jest.spyOn(node.instance(), 'addMarker');
      await flushPromises();

      // then
      expect(addMarkerSpy).toBeCalledWith('foo', 'op-selectable');
      expect(addMarkerSpy).toBeCalledWith('bar', 'op-selectable');
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

    it('should add flow node state overlays if provided', async () => {
      // given
      const flowNodeStateOverlays = [
        {id: 'foo', state: ACTIVITY_STATE.INCIDENT},
        {id: 'bar', state: ACTIVITY_STATE.ACTIVE}
      ];
      const node = shallow(<Diagram workflowId={workflowId} theme={'light'} />);
      await flushPromises();
      const overlaysAddSpy = jest.spyOn(
        node.instance(),
        'addFlowNodeStateOverlay'
      );
      const overlaysRemoveSpy = jest.spyOn(
        node.instance().Viewer.overlays,
        'remove'
      );

      // when
      node.setProps({flowNodeStateOverlays});

      // then
      expect(overlaysAddSpy).toHaveBeenCalledTimes(
        flowNodeStateOverlays.length
      );
      expect(overlaysAddSpy.mock.calls[0][0]).toEqual(flowNodeStateOverlays[0]);
      expect(overlaysAddSpy.mock.calls[1][0]).toEqual(flowNodeStateOverlays[1]);
      expect(overlaysRemoveSpy).toBeCalledWith({
        type: FLOW_NODE_STATE_OVERLAY_ID
      });
    });

    it('should add statistics state overlays if provided', async () => {
      // given
      const flowNodesStatisticsOverlay = [
        {
          activityId: 'Task_162x79i',
          active: 0,
          canceled: 0,
          incidents: 1,
          completed: 0
        },
        {
          activityId: 'Task_1b1r7ow',
          active: 62,
          canceled: 2,
          incidents: 0,
          completed: 0
        }
      ];
      const node = shallow(<Diagram workflowId={workflowId} theme={'light'} />);
      await flushPromises();
      const statisticsOverlaysAddSpy = jest.spyOn(
        node.instance(),
        'addFlowNodesStatisticsOverlays'
      );
      const statisticsOverlaysRemoveSpy = jest.spyOn(
        node.instance().Viewer.overlays,
        'remove'
      );

      // when
      node.setProps({flowNodesStatisticsOverlay});

      // then
      // we clear the statistics overlays
      expect(statisticsOverlaysRemoveSpy).toHaveBeenCalledTimes(4);
      // we add the new overlays
      expect(statisticsOverlaysAddSpy).toHaveBeenCalledTimes(1);
      expect(statisticsOverlaysAddSpy.mock.calls[0][0]).toEqual(
        flowNodesStatisticsOverlay
      );
    });
  });

  describe('detachViewer', () => {
    it('should detach Viewer if it exists', async () => {
      // given
      const node = shallow(<Diagram workflowId={workflowId} theme={'dark'} />);
      const nodeInstance = node.instance();
      await flushPromises();

      // when
      const {detach} = nodeInstance.Viewer;
      nodeInstance.detachViewer();
      node.update();

      // then
      expect(detach).toHaveBeenCalledTimes(1);
      expect(node.state().isViewerLoaded).toBe(false);
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

  describe('addMarker', () => {
    it('should add marker to the canvas', async () => {
      // given
      const node = shallow(<Diagram workflowId={workflowId} theme={'dark'} />);
      await flushPromises();

      // when
      node.instance().addMarker('foo', 'fooClassName');

      // then
      expect(node.instance().Viewer.canvas.addMarker).toBeCalledWith(
        'foo',
        'fooClassName'
      );
    });
  });

  describe('removeMarker', () => {
    it('should remove marker from the canvas', async () => {
      // given
      const node = shallow(<Diagram workflowId={workflowId} theme={'dark'} />);
      await flushPromises();

      // when
      node.instance().removeMarker('foo', 'fooClassName');

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
      const removeMarkerSpy = jest.spyOn(node.instance(), 'removeMarker');
      const addMarkerSpy = jest.spyOn(node.instance(), 'addMarker');

      // when
      node.instance().handleSelectedFlowNode('foo', 'bar');

      // then
      expect(removeMarkerSpy).toBeCalledWith('bar', 'op-selected');
      expect(addMarkerSpy).toBeCalledWith('foo', 'op-selected');
    });

    it("should not remove previous node if it's not provided", async () => {
      // given
      const node = shallow(<Diagram workflowId={workflowId} theme={'dark'} />);
      await flushPromises();
      const removeMarkerSpy = jest.spyOn(node.instance(), 'removeMarker');
      const addMarkerSpy = jest.spyOn(node.instance(), 'addMarker');

      // when
      node.instance().handleSelectedFlowNode('foo');

      // then
      expect(removeMarkerSpy).not.toBeCalled();
      expect(addMarkerSpy).toBeCalledWith('foo', 'op-selected');
    });

    it("should not add new node if it's not provided", async () => {
      // given
      const node = shallow(<Diagram workflowId={workflowId} theme={'dark'} />);
      await flushPromises();
      const removeMarkerSpy = jest.spyOn(node.instance(), 'removeMarker');
      const addMarkerSpy = jest.spyOn(node.instance(), 'addMarker');

      // when
      node.instance().handleSelectedFlowNode(null, 'bar');

      // then
      expect(removeMarkerSpy).toBeCalledWith('bar', 'op-selected');
      expect(addMarkerSpy).not.toBeCalled();
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

  describe('addFlowNodeStateOverlay', () => {
    it('should add incident overlay', async () => {
      // given
      const flowNodeStateOverlay = {id: 'foo', state: ACTIVITY_STATE.INCIDENT};
      const node = shallow(<Diagram workflowId={workflowId} theme={'light'} />);
      await flushPromises();

      // when
      node.instance().addFlowNodeStateOverlay(flowNodeStateOverlay);

      // then
      const overlaysAddSpy = node.instance().Viewer.overlays.add;
      expect(overlaysAddSpy.mock.calls[0][0]).toBe('foo');
      expect(overlaysAddSpy.mock.calls[0][1]).toBe(FLOW_NODE_STATE_OVERLAY_ID);
      expect(
        overlaysAddSpy.mock.calls[0][2].html.src.replace(
          'http://localhost/',
          ''
        )
      ).toBe(incidentIcon);
      expect(overlaysAddSpy.mock.calls[0][2]).toMatchSnapshot();
    });

    it('should add active overlay', async () => {
      // given
      const flowNodeStateOverlay = {id: 'foo', state: ACTIVITY_STATE.ACTIVE};
      const node = shallow(<Diagram workflowId={workflowId} theme={'light'} />);
      await flushPromises();

      // when
      node.instance().addFlowNodeStateOverlay(flowNodeStateOverlay);

      // then
      const overlaysAddSpy = node.instance().Viewer.overlays.add;
      expect(overlaysAddSpy.mock.calls[0][0]).toBe('foo');
      expect(overlaysAddSpy.mock.calls[0][1]).toBe(FLOW_NODE_STATE_OVERLAY_ID);
      expect(
        overlaysAddSpy.mock.calls[0][2].html.src.replace(
          'http://localhost/',
          ''
        )
      ).toBe(activeIcon);
      expect(overlaysAddSpy.mock.calls[0][2]).toMatchSnapshot();
    });

    it('should add completed light overlay', async () => {
      // given
      const flowNodeStateOverlay = {id: 'foo', state: ACTIVITY_STATE.COMPLETED};
      const node = shallow(<Diagram workflowId={workflowId} theme={'light'} />);
      await flushPromises();

      // when
      node.instance().addFlowNodeStateOverlay(flowNodeStateOverlay);

      // then
      const overlaysAddSpy = node.instance().Viewer.overlays.add;
      expect(overlaysAddSpy.mock.calls[0][0]).toBe('foo');
      expect(overlaysAddSpy.mock.calls[0][1]).toBe(FLOW_NODE_STATE_OVERLAY_ID);
      expect(
        overlaysAddSpy.mock.calls[0][2].html.src.replace(
          'http://localhost/',
          ''
        )
      ).toBe(completedLightIcon);
      expect(overlaysAddSpy.mock.calls[0][2]).toMatchSnapshot();
    });

    it('should add completed dark overlay', async () => {
      // given
      const flowNodeStateOverlay = {id: 'foo', state: ACTIVITY_STATE.COMPLETED};
      const node = shallow(<Diagram workflowId={workflowId} theme={'dark'} />);
      await flushPromises();

      // when
      node.instance().addFlowNodeStateOverlay(flowNodeStateOverlay);

      // then
      const overlaysAddSpy = node.instance().Viewer.overlays.add;
      expect(overlaysAddSpy.mock.calls[0][0]).toBe('foo');
      expect(overlaysAddSpy.mock.calls[0][1]).toBe(FLOW_NODE_STATE_OVERLAY_ID);
      expect(
        overlaysAddSpy.mock.calls[0][2].html.src.replace(
          'http://localhost/',
          ''
        )
      ).toBe(completedDarkIcon);
      expect(overlaysAddSpy.mock.calls[0][2]).toMatchSnapshot();
    });

    it('should add canceled light overlay', async () => {
      // given
      const flowNodeStateOverlay = {
        id: 'foo',
        state: ACTIVITY_STATE.TERMINATED
      };
      const node = shallow(<Diagram workflowId={workflowId} theme={'light'} />);
      await flushPromises();

      // when
      node.instance().addFlowNodeStateOverlay(flowNodeStateOverlay);

      // then
      const overlaysAddSpy = node.instance().Viewer.overlays.add;
      expect(overlaysAddSpy.mock.calls[0][0]).toBe('foo');
      expect(overlaysAddSpy.mock.calls[0][1]).toBe(FLOW_NODE_STATE_OVERLAY_ID);
      expect(
        overlaysAddSpy.mock.calls[0][2].html.src.replace(
          'http://localhost/',
          ''
        )
      ).toBe(canceledLightIcon);
      expect(overlaysAddSpy.mock.calls[0][2]).toMatchSnapshot();
    });

    it('should add terminated dark overlay', async () => {
      // given
      const flowNodeStateOverlay = {
        id: 'foo',
        state: ACTIVITY_STATE.TERMINATED
      };
      const node = shallow(<Diagram workflowId={workflowId} theme={'dark'} />);
      await flushPromises();

      // when
      node.instance().addFlowNodeStateOverlay(flowNodeStateOverlay);

      // then
      const overlaysAddSpy = node.instance().Viewer.overlays.add;
      expect(overlaysAddSpy.mock.calls[0][0]).toBe('foo');
      expect(overlaysAddSpy.mock.calls[0][1]).toBe(FLOW_NODE_STATE_OVERLAY_ID);
      expect(
        overlaysAddSpy.mock.calls[0][2].html.src.replace(
          'http://localhost/',
          ''
        )
      ).toBe(canceledDarkIcon);
      expect(overlaysAddSpy.mock.calls[0][2]).toMatchSnapshot();
    });
  });

  describe('addFlowNodesStatisticsOverlays', () => {
    it('should statistics overlays with incidents', async () => {
      // given
      const flowNodesStatisticsOverlay = [
        {
          activityId: 'ServiceTask_1un6ye3',
          active: 0,
          canceled: 0,
          incidents: 7,
          completed: 0
        }
      ];
      const node = shallow(<Diagram workflowId={workflowId} theme={'light'} />);
      await flushPromises();

      // when
      node.setProps({flowNodesStatisticsOverlay});

      // then
      const overlaysAddSpy = node.instance().Viewer.overlays.add;
      expect(overlaysAddSpy.mock.calls[0][0]).toBe('ServiceTask_1un6ye3');
      expect(overlaysAddSpy.mock.calls[0][1]).toBe(
        INCIDENTS_STATISTICS_OVERLAY_ID
      );

      // position, color and content
      expect(
        overlaysAddSpy.mock.calls[0][2].html.style['background-color']
      ).toBe('rgb(255, 61, 61)');
      expect(overlaysAddSpy.mock.calls[0][2].position.top).toBe(undefined);
      expect(overlaysAddSpy.mock.calls[0][2].position.right).toBe(0);
      expect(overlaysAddSpy.mock.calls[0][2].html.textContent).toBe('7');
      expect(overlaysAddSpy.mock.calls[0][2]).toMatchSnapshot();
    });

    it('should statistics overlays with active', async () => {
      // given
      const flowNodesStatisticsOverlay = [
        {
          activityId: 'ServiceTask_1un6ye3',
          active: 7,
          canceled: 0,
          incidents: 0,
          completed: 0
        }
      ];
      const node = shallow(<Diagram workflowId={workflowId} theme={'light'} />);
      await flushPromises();

      // when
      node.setProps({flowNodesStatisticsOverlay});

      // then
      const overlaysAddSpy = node.instance().Viewer.overlays.add;
      expect(overlaysAddSpy).toHaveBeenCalledTimes(1);

      expect(overlaysAddSpy.mock.calls[0][0]).toBe('ServiceTask_1un6ye3');
      expect(overlaysAddSpy.mock.calls[0][1]).toBe(
        ACTIVE_STATISTICS_OVERLAY_ID
      );
      // position and color
      expect(
        overlaysAddSpy.mock.calls[0][2].html.style['background-color']
      ).toBe('rgb(16, 208, 112)');
      expect(overlaysAddSpy.mock.calls[0][2].position.top).toBe(undefined);
      expect(overlaysAddSpy.mock.calls[0][2].position.left).toBe(0);
      expect(overlaysAddSpy.mock.calls[0][2].html.textContent).toBe('7');
      expect(overlaysAddSpy.mock.calls[0][2]).toMatchSnapshot();
    });

    it('should statistics overlays with completed state', async () => {
      // given
      const flowNodesStatisticsOverlay = [
        {
          activityId: 'ServiceTask_1un6ye3',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 7
        }
      ];
      const node = shallow(<Diagram workflowId={workflowId} theme={'light'} />);
      await flushPromises();

      // when
      node.setProps({flowNodesStatisticsOverlay});

      // then
      const overlaysAddSpy = node.instance().Viewer.overlays.add;
      expect(overlaysAddSpy).toHaveBeenCalledTimes(1);
      expect(overlaysAddSpy.mock.calls[0][0]).toBe('ServiceTask_1un6ye3');
      expect(overlaysAddSpy.mock.calls[0][1]).toBe(
        COMPLETED_STATISTICS_OVERLAY_ID
      );
      expect(overlaysAddSpy.mock.calls[0][2].html.textContent).toBe('7');
      expect(overlaysAddSpy.mock.calls[0][2]).toMatchSnapshot();
    });

    it('should statistics overlays with canceled state', async () => {
      // given
      const flowNodesStatisticsOverlay = [
        {
          activityId: 'ServiceTask_1un6ye3',
          active: 0,
          canceled: 7,
          incidents: 0,
          completed: 0
        }
      ];
      const node = shallow(<Diagram workflowId={workflowId} theme={'light'} />);
      await flushPromises();

      // when
      node.setProps({flowNodesStatisticsOverlay});

      // then
      const overlaysAddSpy = node.instance().Viewer.overlays.add;
      expect(overlaysAddSpy).toHaveBeenCalledTimes(1);
      expect(overlaysAddSpy.mock.calls[0][0]).toBe('ServiceTask_1un6ye3');
      expect(overlaysAddSpy.mock.calls[0][1]).toBe(
        CANCELED_STATISTICS_OVERLAY_ID
      );
      expect(overlaysAddSpy.mock.calls[0][2].html.textContent).toBe('7');
      expect(overlaysAddSpy.mock.calls[0][2]).toMatchSnapshot();
    });
  });
});
