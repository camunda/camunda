import React from 'react';
import {shallow, mount} from 'enzyme';

import {createActivity, flushPromises} from 'modules/testUtils';
import {Colors, ThemeProvider} from 'modules/theme';
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
import {parsedDiagram} from 'modules/utils/bpmn';

import Diagram from './Diagram';
import DiagramControls from './DiagramControls';
import * as Styled from './styled';

const mockProps = {
  definitions: parsedDiagram.definitions
};

function mountNode(customProps = {}) {
  return mount(
    <ThemeProvider>
      <Diagram {...mockProps} {...customProps} />
    </ThemeProvider>
  );
}

function shallowRenderNode(customProps = {}) {
  return shallow(
    <Diagram.WrappedComponent theme="dark" {...mockProps} {...customProps} />
  );
}

jest.mock('modules/utils/bpmn');

describe('Diagram', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  const workflowId = 'some-id';

  it('should render Diagram with controls', () => {
    // given
    const node = mountNode();

    // then
    const DiagramCanvasNode = node.find(Styled.DiagramCanvas);
    expect(DiagramCanvasNode).toHaveLength(1);
    const DiagramControlsNode = node.find(DiagramControls);
    expect(DiagramControlsNode).toHaveLength(1);
  });

  it('should reinitiate the Viewer when theme changes', async () => {
    // given
    const node = shallowRenderNode({theme: 'dark'});
    const oldViewer = node.instance().Viewer;

    // when
    node.setProps({theme: 'light'});

    // then
    const newViewer = node.instance().Viewer;
    expect(newViewer).not.toBe(oldViewer);
    expect(newViewer.importDefinitions.mock.calls[0][0]).toEqual(
      mockProps.definitions
    );
  });

  it('should reinitiate the Viewer when the definitions change', async () => {
    // given
    const node = shallowRenderNode({definitions: {}, theme: 'dark'});
    const oldViewer = node.instance().Viewer;

    // when
    node.setProps({definitions: mockProps.definitions});

    // then
    const newViewer = node.instance().Viewer;
    expect(newViewer).not.toBe(oldViewer);
    expect(newViewer.importDefinitions.mock.calls[0][0]).toEqual(
      mockProps.definitions
    );
  });

  describe('selected flownode', () => {
    it('should remove marker when an already selected node gets selected', () => {
      // given
      const node = shallowRenderNode({selectedFlowNode: 'nodeA'});
      const canvas = node.instance().Viewer.get('canvas');

      // when
      node.setProps({selectedFlowNode: 'nodeB'});

      // then
      expect(canvas.addMarker).toHaveBeenCalledWith('nodeA', 'op-selected');
      expect(canvas.removeMarker).toHaveBeenCalledWith('nodeA', 'op-selected');
    });

    it('should remove marker when an already selected node gets selected', () => {
      // given
      const node = shallowRenderNode({selectedFlowNode: null});
      const canvas = node.instance().Viewer.get('canvas');

      // when
      node.setProps({selectedFlowNode: 'nodeA'});

      // then
      expect(canvas.addMarker).toHaveBeenCalledWith('nodeA', 'op-selected');
    });

    it.skip('should handle selecting a selectable flownode', () => {});

    it.skip('should handle selecting a non-selectable flownode', () => {});
  });

  describe('selectable flownodes', () => {
    it('should add a marker for each selectable flownode', () => {
      // given
      const node = shallowRenderNode({selectableFlowNodes: ['nodeA', 'nodeB']});
      const canvas = node.instance().Viewer.get('canvas');

      // when
      node.setProps({selectedFlowNode: 'nodeA'});

      // then
      expect(canvas.addMarker).toHaveBeenCalledWith('nodeA', 'op-selectable');
      expect(canvas.addMarker).toHaveBeenCalledWith('nodeB', 'op-selectable');
    });
  });

  describe.skip('selected flownode markers', () => {
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

  describe.skip('flownode selection interaction', () => {
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

  describe('flownode state overlays', () => {
    const flowNodeStateOverlays = [
      createActivity({state: ACTIVITY_STATE.ACTIVE}),
      createActivity({state: ACTIVITY_STATE.INCIDENT}),
      createActivity({state: ACTIVITY_STATE.COMPLETED}),
      createActivity({state: ACTIVITY_STATE.TERMINATED})
    ];

    it('should add flownode state overlays when the diagram loads', () => {
      // given
      const node = shallowRenderNode({flowNodeStateOverlays});
      const overlays = node.instance().Viewer.get('overlays');

      // then
      flowNodeStateOverlays.forEach(overlay => {
        expect(overlays.add).toHaveBeenCalledWith(
          overlay.id,
          expect.any(String),
          expect.any(Object)
        );
      });
    });

    it('should add flownode state overlays when they props value change', () => {
      // given
      const node = shallowRenderNode(flowNodeStateOverlays);
      const newOverlays = [
        createActivity({state: ACTIVITY_STATE.ACTIVE}),
        createActivity({state: ACTIVITY_STATE.INCIDENT})
      ];
      node.setProps({flowNodeStateOverlays: newOverlays});
      const overlays = node.instance().Viewer.get('overlays');

      // then
      expect(overlays.remove.mock.calls[0][0].type).toBe(
        FLOW_NODE_STATE_OVERLAY_ID
      );
      newOverlays.forEach(overlay => {
        expect(overlays.add).toHaveBeenCalledWith(
          overlay.id,
          expect.any(String),
          expect.any(Object)
        );
      });
    });
  });

  describe("viewer's theme", () => {
    it('should initiate dark BPMNViewer if theme is dark', () => {
      // given
      const node = shallowRenderNode({theme: 'dark'});
      const nodeInstance = node.instance();

      //then
      const {container, bpmnRenderer} = nodeInstance.Viewer;
      expect(container).toBe(nodeInstance.myRef.current);
      expect(bpmnRenderer).toEqual({
        defaultFillColor: Colors.uiDark02,
        defaultStrokeColor: Colors.darkDiagram
      });
    });

    it('should initiate light BPMNViewer if theme is light', () => {
      // given
      const node = shallowRenderNode({theme: 'light'});
      const nodeInstance = node.instance();

      //then
      const {container, bpmnRenderer} = nodeInstance.Viewer;
      expect(container).toBe(nodeInstance.myRef.current);
      expect(bpmnRenderer).toEqual({
        defaultFillColor: Colors.uiLight04,
        defaultStrokeColor: Colors.uiLight06
      });
    });
  });

  describe.skip('addFlowNodesStatisticsOverlays', () => {
    it('should add statistics state overlays if provided', () => {
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
