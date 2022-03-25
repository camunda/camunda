/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
// @ts-expect-error ts-migrate(7016) FIXME: Try `npm install @types/bpmn-js` if it exists or a... Remove this comment to see the full error message
import BPMNViewer from 'bpmn-js/lib/NavigatedViewer';
import {flatMap, isEqual} from 'lodash';
import {withTheme, DefaultTheme} from 'styled-components';

import * as Styled from './styled';
import DiagramControls from './DiagramControls';
import {StateOverlay} from './StateOverlay';
import {StatisticOverlay} from './StatisticOverlay';
import {PopoverOverlay} from './PopoverOverlay';
import {isNonSelectableFlowNode} from 'modules/bpmn-js/isNonSelectableFlowNode';
import {BpmnJSElement} from 'modules/bpmn-js/BpmnJS';

function isMultiInstance(element: BpmnJSElement) {
  return (
    element.businessObject.loopCharacteristics?.$type ===
    'bpmn:MultiInstanceLoopCharacteristics'
  );
}

type Props = {
  theme: DefaultTheme;
  definitions: any;
  clickableFlowNodes?: string[];
  selectableFlowNodes?: string[];
  processedSequenceFlows?: string[];
  selectedFlowNodeId?: string;
  onFlowNodeSelection?: (
    elementId: BpmnJSElement['id'] | undefined,
    isMultiInstance?: boolean
  ) => void;
  flowNodeStateOverlays?: {
    id: string;
    state: InstanceEntityState;
  }[];
  flowNodesStatistics?: {
    activityId: string;
    active?: number;
    incidents?: number;
    completed?: number;
    canceled?: number;
  }[];
  hidePopover?: boolean;
};

type State = {
  isViewerLoaded: boolean;
  isViewboxChanging: boolean;
};

class Diagram extends React.PureComponent<Props, State> {
  Viewer = null;
  containerRef = React.createRef<HTMLDivElement>();

  state = {
    isViewerLoaded: false,
    isViewboxChanging: false,
  };

  componentDidMount() {
    this.initViewer();
  }

  componentDidUpdate({
    theme: prevTheme,
    definitions: prevDefinitions,
    selectedFlowNodeId,
    processedSequenceFlows: prevSequenceFlows,
    selectableFlowNodes: prevSelectableFlowNodes,
  }: Props) {
    const hasNewDefinitions = this.props.definitions !== prevDefinitions;
    const hasNewTheme = this.props.theme !== prevTheme;
    const hasNewSelectableFlowNoes = !isEqual(
      this.props.selectableFlowNodes,
      prevSelectableFlowNodes
    );

    const {processedSequenceFlows: currentSequenceFlows} = this.props;

    if (hasNewTheme || hasNewDefinitions) {
      return this.resetViewer();
    }

    if (hasNewSelectableFlowNoes) {
      return this.resetViewer();
    }

    const hasSelectedFlowNodeChanged =
      this.props.selectedFlowNodeId !== selectedFlowNodeId;

    const hasProcessedSequenceFlowsChanged =
      prevSequenceFlows !== currentSequenceFlows;

    // In case only the selectedFlowNode changed.
    // This also means that the Viewer is already initiated so we can safely
    // call this.handleSelectedFlowNode.
    if (this.state.isViewerLoaded && hasSelectedFlowNodeChanged) {
      this.handleSelectedFlowNode(
        this.props.selectedFlowNodeId,
        selectedFlowNodeId
      );
    }
    if (
      this.state.isViewerLoaded &&
      hasProcessedSequenceFlowsChanged &&
      prevSequenceFlows !== undefined &&
      currentSequenceFlows !== undefined
    ) {
      this.handleProcessedSequenceFlowsChange(
        prevSequenceFlows,
        currentSequenceFlows
      );
    }
  }

  // 1- state.isViewerLoaded => true
  // 2- canvas.resized() && canvas.zoom('fit-viewport', 'auto')
  handleDiagramLoad = () => {
    this.setState({
      isViewerLoaded: true,
    });

    this.handleZoomReset();

    this.addViewboxListeners();

    if (this.props.selectableFlowNodes) {
      this.props.onFlowNodeSelection && this.addElementClickListeners();
      this.handleSelectableFlowNodes(this.props.selectableFlowNodes);
    }

    if (this.props.selectedFlowNodeId) {
      this.handleSelectedFlowNode(this.props.selectedFlowNodeId);
    }

    if (this.props.processedSequenceFlows) {
      this.handleProcessedSequenceFlows(this.props.processedSequenceFlows);
    }

    this.handleNonSelectableFlowNodes();
  };

  initViewer = () => {
    // colors config for bpmnRenderer
    this.Viewer = new BPMNViewer({
      container: this.containerRef.current,
      bpmnRenderer: this.props.theme.colors.modules.diagram,
    });

    // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
    this.Viewer.importDefinitions(this.props.definitions)
      .then(this.handleDiagramLoad)
      .catch((e: any) => {
        return console.log('Error rendering diagram:', e);
      });
  };

  resetViewer = () => {
    if (this.Viewer) {
      // if there is a viewer detach it, update the state then init a new viewer
      // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
      this.Viewer.detach();
      return this.setState(
        {
          isViewerLoaded: false,
        },
        this.initViewer
      );
    }
  };

  handleZoom = (step: number) => {
    // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
    this.Viewer.get('zoomScroll').stepZoom(step);
  };

  handleZoomIn = () => {
    this.handleZoom(0.1);
  };

  handleZoomOut = () => {
    this.handleZoom(-0.1);
  };

  handleZoomReset = () => {
    // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
    const canvas = this.Viewer.get('canvas');
    canvas.resized();
    canvas.zoom('fit-viewport', 'auto');
  };

  addMarker = (id: string, className: string) => {
    // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
    const canvas = this.Viewer.get('canvas');
    // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
    const elementRegistry = this.Viewer.get('elementRegistry');

    if (elementRegistry.get(id) !== undefined) {
      canvas.addMarker(id, className);
      const gfx = elementRegistry.getGraphics(id).querySelector('.djs-outline');
      gfx.setAttribute('rx', '14px');
      gfx.setAttribute('ry', '14px');
    }
  };

  removeMarker = (id: string, className: string) => {
    // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
    const canvas = this.Viewer.get('canvas');
    // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
    const elementRegistry = this.Viewer.get('elementRegistry');

    if (elementRegistry.get(id) !== undefined) {
      canvas.removeMarker(id, className);
    }
  };

  colorElement = (id: string, color?: string) => {
    // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
    var elementRegistry = this.Viewer.get('elementRegistry');
    // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
    var graphicsFactory = this.Viewer.get('graphicsFactory');
    var element: BpmnJSElement | undefined = elementRegistry.get(id);
    if (element?.di !== undefined) {
      element.di.set('stroke', color);

      var gfx = elementRegistry.getGraphics(element);
      graphicsFactory.update('connection', element, gfx);
    }
  };

  handleProcessedSequenceFlows = (processedSequenceFlows: any) => {
    const {theme} = this.props;

    processedSequenceFlows.forEach((id: any) => {
      this.colorElement(id, theme.colors.selections);
    });
  };

  handleProcessedSequenceFlowsChange = (
    prevSequenceFlows: string[],
    currentSequenceFlows: string[]
  ) => {
    prevSequenceFlows?.forEach((id: string) => {
      this.colorElement(id);
    });

    currentSequenceFlows.forEach((id: string) => {
      const {theme} = this.props;

      this.colorElement(id, theme.colors.selections);
    });
  };

  handleSelectableFlowNodes = (selectableFlowNodes: any) => {
    selectableFlowNodes.forEach((id: any) => {
      this.addMarker(id, 'op-selectable');
    });
  };

  handleNonSelectableFlowNodes = () => {
    // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
    const elementRegistry = this.Viewer.get('elementRegistry');

    elementRegistry.forEach((element: BpmnJSElement) => {
      if (isNonSelectableFlowNode(element, this.props.selectableFlowNodes)) {
        const gfx = elementRegistry.getGraphics(element);
        gfx.style.cursor = 'not-allowed';
      }
    });
  };

  handleSelectedFlowNode = (
    selectedFlowNodeId?: string,
    prevSelectedFlowNodeId?: string
  ) => {
    // clear previously selected flow node marker is there is one
    if (prevSelectedFlowNodeId) {
      this.removeMarker(prevSelectedFlowNodeId, 'op-selected');
    }

    // add marker for newly selected flow node if there is one
    if (selectedFlowNodeId) {
      this.addMarker(selectedFlowNodeId, 'op-selected');
    }
  };

  handleElementClick = ({element}: {element: BpmnJSElement}) => {
    const {selectedFlowNodeId, selectableFlowNodes} = this.props;

    if (
      isNonSelectableFlowNode(element, selectableFlowNodes) ||
      selectableFlowNodes === undefined
    ) {
      // Don't handle click if flow node is not selectable.
      return;
    }

    const isSelectableElement =
      selectableFlowNodes.filter((id) => id === element.id).length > 0;

    // Only select the flownode if it's selectable and if it's not already selected.
    if (isSelectableElement && element.id !== selectedFlowNodeId) {
      return this.props.onFlowNodeSelection?.(
        element.id,
        isMultiInstance(element)
      );
    } else if (selectedFlowNodeId) {
      this.props.onFlowNodeSelection?.(undefined);
    }
  };

  addElementClickListeners = () => {
    // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
    const eventBus = this.Viewer.get('eventBus');
    eventBus.on('element.click', this.handleElementClick);
  };

  addViewboxListeners = () => {
    // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
    const eventBus = this.Viewer.get('eventBus');
    eventBus.on('canvas.viewbox.changing', () => {
      this.setState({isViewboxChanging: true});
    });

    eventBus.on('canvas.viewbox.changed', () => {
      this.setState({isViewboxChanging: false});
    });
  };

  // @ts-expect-error ts-migrate(7019) FIXME: Rest parameter 'args' implicitly has an 'any[]' ty... Remove this comment to see the full error message
  handleOverlayAdd = (...args) => {
    const viewer = this.Viewer;
    // @ts-expect-error
    if (viewer.get('elementRegistry').get(args[0]) !== undefined) {
      // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
      viewer.get('overlays').add(...args);
    }
  };

  // @ts-expect-error ts-migrate(7019) FIXME: Rest parameter 'args' implicitly has an 'any[]' ty... Remove this comment to see the full error message
  handleOverlayClear = (...args) => this.Viewer.get('overlays').remove(...args);

  renderFlowNodeStateOverlays = (overlayProps: any) => {
    const {flowNodeStateOverlays} = this.props;
    if (!flowNodeStateOverlays) {
      return null;
    }

    return flowNodeStateOverlays.map((stateData) => (
      <StateOverlay key={stateData.id} {...stateData} {...overlayProps} />
    ));
  };

  renderStatisticsOverlays = (overlayProps: any) => {
    const {flowNodesStatistics} = this.props;
    if (flowNodesStatistics && flowNodesStatistics.length === 0) {
      return null;
    }

    const states = ['active', 'incidents', 'canceled', 'completed'];

    return flatMap(flowNodesStatistics, (statistic: any) => {
      return states.map((state) => (
        <StatisticOverlay
          key={`${state} ${statistic.activityId}`}
          statistic={statistic}
          state={state}
          {...overlayProps}
        />
      ));
    });
  };

  render() {
    const overlayProps = {
      onOverlayAdd: this.handleOverlayAdd,
      onOverlayClear: this.handleOverlayClear,
      isViewerLoaded: this.state.isViewerLoaded,
      theme: this.props.theme,
    };

    const {selectedFlowNodeId} = this.props;
    return (
      <Styled.Diagram data-testid="diagram">
        <Styled.DiagramCanvas ref={this.containerRef} />
        {this.Viewer && (
          <DiagramControls
            handleZoomIn={this.handleZoomIn}
            handleZoomOut={this.handleZoomOut}
            handleZoomReset={this.handleZoomReset}
          />
        )}

        {selectedFlowNodeId &&
          this.state.isViewerLoaded &&
          this.Viewer &&
          !this.state.isViewboxChanging &&
          !this.props.hidePopover && (
            <PopoverOverlay
              selectedFlowNodeRef={
                // @ts-expect-error
                this.Viewer.get('elementRegistry').getGraphics(
                  selectedFlowNodeId
                )
              }
            />
          )}
        {this.renderFlowNodeStateOverlays(overlayProps)}
        {this.renderStatisticsOverlays(overlayProps)}
      </Styled.Diagram>
    );
  }
}

export default withTheme(Diagram);
export type {BpmnJSElement};
