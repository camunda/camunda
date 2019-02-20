import React from 'react';
import PropTypes from 'prop-types';
import BPMNViewer from 'bpmn-js/lib/NavigatedViewer';
import {flatMap} from 'lodash';

import {themed} from 'modules/theme';

import {STATE} from 'modules/constants';

import * as Styled from './styled';
import DiagramControls from './DiagramControls';
import StateOverlay from './StateOverlay';
import StatisticOverlay from './StatisticOverlay';
import PopoverOverlay from './PopoverOverlay';
import {getDiagramColors} from './service';

class Diagram extends React.PureComponent {
  static propTypes = {
    theme: PropTypes.string.isRequired,
    definitions: PropTypes.object.isRequired,
    onDiagramLoaded: PropTypes.func,
    clickableFlowNodes: PropTypes.arrayOf(PropTypes.string),
    selectableFlowNodes: PropTypes.arrayOf(PropTypes.string),
    selectedFlowNodeId: PropTypes.string,
    selectedFlowNodeName: PropTypes.string,
    onFlowNodeSelection: PropTypes.func,
    flowNodeStateOverlays: PropTypes.arrayOf(
      PropTypes.shape({
        id: PropTypes.string.isRequired,
        state: PropTypes.oneOf(Object.keys(STATE)).isRequired
      })
    ),
    flowNodesStatistics: PropTypes.arrayOf(
      PropTypes.shape({
        activityId: PropTypes.string.isRequired,
        active: PropTypes.number,
        incidents: PropTypes.number,
        completed: PropTypes.number,
        canceled: PropTypes.number
      })
    ),
    metadata: PropTypes.object
  };

  constructor(props) {
    super(props);
    this.Viewer = null;
    this.myRef = React.createRef();
  }

  state = {
    isViewerLoaded: false
  };

  componentDidMount() {
    this.initViewer();
  }

  componentDidUpdate({
    theme: prevTheme,
    definitions: prevDefinitions,
    selectedFlowNodeId
  }) {
    const hasNewDefinitions = this.props.definitions !== prevDefinitions;
    const hasNewTheme = this.props.theme !== prevTheme;

    if (hasNewTheme || hasNewDefinitions) {
      return this.resetViewer();
    }

    const hasSelectedFlowNodeChanged =
      this.props.selectedFlowNodeId !== selectedFlowNodeId;

    // In case only the selectedFlowNode changed.
    // This also means that the Viewer is already initiated so we can safely
    // call this.handleSelectedFlowNode.
    if (this.state.isViewerLoaded && hasSelectedFlowNodeChanged) {
      this.handleSelectedFlowNode(
        this.props.selectedFlowNodeId,
        selectedFlowNodeId
      );
    }
  }

  // 1- state.isViewerLoaded => true
  // 2- canvas.resized() && canvas.zoom('fit-viewport', 'auto')
  handleDiagramLoad = e => {
    if (e) {
      return console.log('Error rendering diagram:', e);
    }

    this.setState({
      isViewerLoaded: true
    });

    this.handleZoomReset();

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
      this.handleSelectedFlowNode(this.props.selectedFlowNodeId);
    }
  };

  initViewer = () => {
    // colors config for bpmnRenderer
    this.Viewer = new BPMNViewer({
      container: this.myRef.current,
      bpmnRenderer: getDiagramColors(this.props.theme)
    });

    this.Viewer.importDefinitions(
      this.props.definitions,
      this.handleDiagramLoad
    );
  };

  resetViewer = () => {
    if (this.Viewer) {
      // if there is a viewer detatch it, update the state then init a new viewer
      this.Viewer.detach();
      return this.setState(
        {
          isViewerLoaded: false
        },
        this.initViewer
      );
    }

    this.initViewer();
  };

  containerRef = node => {
    this.containerNode = node;
  };

  handleZoom = step => {
    this.Viewer.get('zoomScroll').stepZoom(step);
  };

  handleZoomIn = () => {
    this.handleZoom(0.1);
  };

  handleZoomOut = () => {
    this.handleZoom(-0.1);
  };

  handleZoomReset = () => {
    const canvas = this.Viewer.get('canvas');
    canvas.resized();
    canvas.zoom('fit-viewport', 'auto');
  };

  addMarker = (id, className) => {
    const canvas = this.Viewer.get('canvas');
    const elementRegistry = this.Viewer.get('elementRegistry');
    canvas.addMarker(id, className);
    const gfx = elementRegistry.getGraphics(id).querySelector('.djs-outline');
    gfx.setAttribute('rx', '14px');
    gfx.setAttribute('ry', '14px');
  };

  removeMarker = (id, className) => {
    const canvas = this.Viewer.get('canvas');
    canvas.removeMarker(id, className);
  };

  handleSelectableFlowNodes = selectableFlowNodes => {
    selectableFlowNodes.forEach(id => {
      this.addMarker(id, 'op-selectable');
    });
  };

  handleSelectedFlowNode = (selectedFlowNodeId, prevSelectedFlowNodeId) => {
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
    const {selectedFlowNodeId} = this.props;
    const isSelectableElement =
      this.props.selectableFlowNodes.filter(id => id === element.id).length > 0;

    // Only select the flownode if it's selectable and if it's not already selected.
    if (isSelectableElement && element.id !== selectedFlowNodeId) {
      return this.props.onFlowNodeSelection(element.id);
    } else if (selectedFlowNodeId) {
      this.props.onFlowNodeSelection(null);
    }
  };

  addElementClickListeners = () => {
    const eventBus = this.Viewer.get('eventBus');
    eventBus.on('element.click', this.handleElementClick);
  };

  handleOverlayAdd = (...args) => {
    this.Viewer.get('overlays').add(...args);
  };

  handleOverlayClear = (...args) => this.Viewer.get('overlays').remove(...args);

  renderFlowNodeStateOverlays = overlayProps => {
    const {flowNodeStateOverlays} = this.props;

    if (!flowNodeStateOverlays) {
      return null;
    }

    return flowNodeStateOverlays.map(stateData => (
      <StateOverlay key={stateData.id} {...stateData} {...overlayProps} />
    ));
  };

  renderStatisticsOverlays = overlayProps => {
    const {flowNodesStatistics} = this.props;

    if (!flowNodesStatistics) {
      return null;
    }

    const states = ['active', 'incidents', 'canceled', 'completed'];

    return flatMap(flowNodesStatistics, statistic => {
      return states.map(state => (
        <StatisticOverlay
          key={`${state} ${statistic.activityId}`}
          statistic={statistic}
          state={state}
          {...overlayProps}
        />
      ));
    });
  };

  getFlowNodePosition = flowNodeId => {
    const MIN_HEIGHT = 70;
    const MIN_WIDTH = 190;

    const containerBoundary = this.myRef.current.getBoundingClientRect();

    const element = this.Viewer.get('elementRegistry').getGraphics(flowNodeId);
    const elementBoudary = element.getBoundingClientRect();
    const elementBBox = element.getBBox();

    const spaceToBottom =
      containerBoundary.top +
      containerBoundary.height -
      elementBoudary.top -
      elementBoudary.height;

    if (spaceToBottom > MIN_HEIGHT) {
      return {
        bottom: -16,
        left: elementBBox.width / 2,
        side: 'BOTTOM'
      };
    }

    const spaceToLeft = elementBoudary.left - containerBoundary.left;

    if (spaceToLeft > MIN_WIDTH) {
      return {
        left: -25,
        top: elementBBox.height / 2,
        side: 'LEFT'
      };
    }

    const spaceToTop = elementBoudary.top - containerBoundary.top;

    if (spaceToTop > MIN_HEIGHT) {
      return {
        top: -16,
        left: elementBBox.width / 2,
        side: 'TOP'
      };
    }

    const spaceToRight =
      containerBoundary.width - spaceToLeft - elementBoudary.width;

    if (spaceToRight > MIN_WIDTH) {
      return {
        top: elementBBox.height / 2,
        right: -16,
        side: 'RIGHT'
      };
    }

    return {
      bottom: +16,
      left: elementBBox.width / 2,
      side: 'BOTTOM_MIRROR'
    };
  };

  render() {
    const overlayProps = {
      onOverlayAdd: this.handleOverlayAdd,
      onOverlayClear: this.handleOverlayClear,
      isViewerLoaded: this.state.isViewerLoaded,
      theme: this.props.theme
    };

    return (
      <Styled.Diagram>
        <Styled.DiagramCanvas ref={this.myRef} />
        <DiagramControls
          handleZoomIn={this.handleZoomIn}
          handleZoomOut={this.handleZoomOut}
          handleZoomReset={this.handleZoomReset}
        />
        {this.props.selectedFlowNodeId && this.props.metadata && (
          <PopoverOverlay
            key={this.props.selectedFlowNodeId}
            selectedFlowNode={{
              id: this.props.selectedFlowNodeId,
              name:
                this.props.selectedFlowNodeName || this.props.selectedFlowNodeId
            }}
            metadata={this.props.metadata}
            onFlowNodeSelection={this.props.onFlowNodeSelection}
            position={this.getFlowNodePosition(this.props.selectedFlowNodeId)}
            {...overlayProps}
          />
        )}
        {this.renderFlowNodeStateOverlays(overlayProps)}
        {this.renderStatisticsOverlays(overlayProps)}
      </Styled.Diagram>
    );
  }
}

export default themed(Diagram);
