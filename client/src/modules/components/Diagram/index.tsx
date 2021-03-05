/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
// @ts-expect-error ts-migrate(7016) FIXME: Try `npm install @types/bpmn-js` if it exists or a... Remove this comment to see the full error message
import BPMNViewer from 'bpmn-js/lib/NavigatedViewer';
import {flatMap, isEqual} from 'lodash';
import {withTheme} from 'styled-components';

import {EXPAND_STATE} from 'modules/constants';

import * as Styled from './styled';
import DiagramControls from './DiagramControls';
import StateOverlay from './StateOverlay';
import StatisticOverlay from './StatisticOverlay';
import PopoverOverlay from './PopoverOverlay';

import {getPopoverPosition, isNonSelectableFlowNode} from './service';

type Props = {
  theme?: any;
  definitions: any;
  onDiagramLoaded?: (...args: any[]) => any;
  clickableFlowNodes?: string[];
  selectableFlowNodes?: string[];
  processedSequenceFlows?: string[];
  selectedFlowNodeId?: string;
  selectedFlowNodeName?: string;
  onFlowNodeSelection?: (...args: any[]) => any;
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
  metadata?: any;
  expandState?: 'DEFAULT' | 'EXPANDED' | 'COLLAPSED';
};

type State = any;

class Diagram extends React.PureComponent<Props, State> {
  containerNode: any;

  Viewer = null;
  myRef = React.createRef<HTMLDivElement>();

  state = {
    isViewerLoaded: false,
  };

  componentDidMount() {
    this.initViewer();
  }

  componentDidUpdate({
    theme: prevTheme,
    definitions: prevDefinitions,
    selectedFlowNodeId,
    processedSequenceFlows: prevSequenceFlows,
    expandState: prevExpandState,
    selectableFlowNodes: prevSelectableFlowNodes,
  }: Props) {
    const hasNewDefinitions = this.props.definitions !== prevDefinitions;
    const hasNewTheme = this.props.theme !== prevTheme;
    const hasNewSelectableFlowNoes = !isEqual(
      this.props.selectableFlowNodes,
      prevSelectableFlowNodes
    );

    const {
      expandState,
      processedSequenceFlows: currentSequenceFlows,
    } = this.props;

    if (
      expandState !== prevExpandState &&
      expandState !== EXPAND_STATE.COLLAPSED
    ) {
      return this.resetViewer();
    }

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
    if (this.state.isViewerLoaded && hasProcessedSequenceFlowsChanged) {
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

    if (this.props.expandState !== EXPAND_STATE.COLLAPSED) {
      this.handleZoomReset();
    }

    // in case onDiagramLoaded callback function is provided
    // call it with flowNodesDetails
    if (typeof this.props.onDiagramLoaded === 'function') {
      this.props.onDiagramLoaded();
    }

    if (this.props.selectableFlowNodes) {
      this.props.onFlowNodeSelection && this.addElementClickListeners();
      this.handleSelectableFlowNodes(this.props.selectableFlowNodes);
    }

    if (this.props.selectedFlowNodeId) {
      // @ts-expect-error ts-migrate(2554) FIXME: Expected 2 arguments, but got 1.
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
      container: this.myRef.current,
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

  containerRef = (node: any) => {
    this.containerNode = node;
  };

  handleZoom = (step: any) => {
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

  addMarker = (id: any, className: any) => {
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

  removeMarker = (id: any, className: any) => {
    // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
    const canvas = this.Viewer.get('canvas');
    // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
    const elementRegistry = this.Viewer.get('elementRegistry');

    if (elementRegistry.get(id) !== undefined) {
      canvas.removeMarker(id, className);
    }
  };

  colorElement = (id: any, color: any) => {
    // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
    var elementRegistry = this.Viewer.get('elementRegistry');
    // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
    var graphicsFactory = this.Viewer.get('graphicsFactory');
    var element = elementRegistry.get(id);
    if (element.businessObject && element.businessObject.di) {
      element.businessObject.di.set('stroke', color);

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
    prevSequenceFlows: any,
    currentSequenceFlows: any
  ) => {
    prevSequenceFlows.forEach((id: any) => {
      // @ts-expect-error ts-migrate(2554) FIXME: Expected 2 arguments, but got 1.
      this.colorElement(id);
    });

    currentSequenceFlows.forEach((id: any) => {
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

    elementRegistry.forEach((element: any) => {
      if (isNonSelectableFlowNode(element, this.props.selectableFlowNodes)) {
        const gfx = elementRegistry.getGraphics(element);
        gfx.style.cursor = 'not-allowed';
      }
    });
  };

  handleSelectedFlowNode = (
    selectedFlowNodeId: any,
    prevSelectedFlowNodeId: any
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

  handleElementClick = ({element = {}}) => {
    const {selectedFlowNodeId, selectableFlowNodes} = this.props;

    if (isNonSelectableFlowNode(element, selectableFlowNodes)) {
      // Don't handle click if flow node is not selectable.
      return;
    }

    const isSelectableElement =
      // @ts-expect-error ts-migrate(2532) FIXME: Object is possibly 'undefined'.
      selectableFlowNodes.filter((id) => id === element.id).length > 0;

    // Only select the flownode if it's selectable and if it's not already selected.
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'id' does not exist on type '{}'.
    if (isSelectableElement && element.id !== selectedFlowNodeId) {
      // @ts-expect-error ts-migrate(2722) FIXME: Cannot invoke an object which is possibly 'undefin... Remove this comment to see the full error message
      return this.props.onFlowNodeSelection(element.id);
    } else if (selectedFlowNodeId) {
      // @ts-expect-error ts-migrate(2722) FIXME: Cannot invoke an object which is possibly 'undefin... Remove this comment to see the full error message
      this.props.onFlowNodeSelection(null);
    }
  };

  addElementClickListeners = () => {
    // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
    const eventBus = this.Viewer.get('eventBus');
    eventBus.on('element.click', this.handleElementClick);
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

  getPopoverPosition = (isSummaryPopover: any) => {
    // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
    const flowNode = this.Viewer.get('elementRegistry').getGraphics(
      this.props.selectedFlowNodeId
    );

    return getPopoverPosition(
      {
        diagramContainer: this.myRef.current,
        flowNode,
      },
      isSummaryPopover
    );
  };

  render() {
    const overlayProps = {
      onOverlayAdd: this.handleOverlayAdd,
      onOverlayClear: this.handleOverlayClear,
      isViewerLoaded: this.state.isViewerLoaded,
      theme: this.props.theme,
    };

    const {
      metadata,
      selectedFlowNodeId,
      onFlowNodeSelection,
      selectedFlowNodeName,
    } = this.props;

    return (
      <Styled.Diagram data-testid="diagram">
        <Styled.DiagramCanvas ref={this.myRef} />
        <DiagramControls
          handleZoomIn={this.handleZoomIn}
          handleZoomOut={this.handleZoomOut}
          handleZoomReset={this.handleZoomReset}
        />
        {selectedFlowNodeId &&
          metadata &&
          this.state.isViewerLoaded &&
          this.Viewer && (
            // @ts-expect-error ts-migrate(2769) FIXME: Type '{ bottom: number; left: number; side: string... Remove this comment to see the full error message
            <PopoverOverlay
              key={selectedFlowNodeId}
              selectedFlowNodeId={selectedFlowNodeId}
              selectedFlowNodeName={selectedFlowNodeName}
              metadata={metadata}
              onFlowNodeSelection={onFlowNodeSelection}
              position={this.getPopoverPosition(metadata.isMultiRowPeterCase)}
              {...overlayProps}
            />
          )}
        {this.renderFlowNodeStateOverlays(overlayProps)}
        {this.renderStatisticsOverlays(overlayProps)}
      </Styled.Diagram>
    );
  }
}

export default withTheme(Diagram);
