import React from 'react';
import PropTypes from 'prop-types';
import BPMNViewer from 'bpmn-js/lib/NavigatedViewer';

import {themed} from 'modules/theme';
import {ACTIVITY_STATE} from 'modules/constants';
import incidentIcon from 'modules/components/Icon/diagram-badge-single-instance-incident.svg';
import activeIcon from 'modules/components/Icon/diagram-badge-single-instance-active.svg';
import completedLightIcon from 'modules/components/Icon/diagram-badge-single-instance-completed-light.svg';
import completedDarkIcon from 'modules/components/Icon/diagram-badge-single-instance-completed-dark.svg';
import canceledLightIcon from 'modules/components/Icon/diagram-badge-single-instance-canceled-light.svg';
import canceledDarkIcon from 'modules/components/Icon/diagram-badge-single-instance-canceled-dark.svg';

import * as Styled from './styled';
import DiagramControls from './DiagramControls';
import * as api from 'modules/api/diagram';
import {getDiagramColors, getFlowNodesDetails} from './service';

const iconMap = {
  [ACTIVITY_STATE.INCIDENT]: {light: incidentIcon, dark: incidentIcon},
  [ACTIVITY_STATE.ACTIVE]: {light: activeIcon, dark: activeIcon},
  [ACTIVITY_STATE.COMPLETED]: {
    light: completedLightIcon,
    dark: completedDarkIcon
  },
  [ACTIVITY_STATE.TERMINATED]: {
    light: canceledLightIcon,
    dark: canceledDarkIcon
  }
};

class Diagram extends React.Component {
  static propTypes = {
    theme: PropTypes.string.isRequired,
    workflowId: PropTypes.string.isRequired,
    // callback function called when flowNodesDetails is ready
    onFlowNodesDetailsReady: PropTypes.func,
    clickableFlowNodes: PropTypes.arrayOf(PropTypes.string),
    selectableFlowNodes: PropTypes.arrayOf(PropTypes.string),
    selectedFlowNode: PropTypes.string,
    onFlowNodeSelected: PropTypes.func,
    flowNodeStateOverlay: PropTypes.shape({
      id: PropTypes.string.isRequired,
      state: PropTypes.oneOf(Object.keys(ACTIVITY_STATE)).isRequired
    })
  };

  constructor(props) {
    super(props);
    this.containerNode = null;
    this.Viewer = null;
    this.workflowXML = null;
  }

  async componentDidMount() {
    await this.fetchAndSetWorkflowXML();
    this.initViewer();
  }

  async componentDidUpdate({
    theme: prevTheme,
    workflowId: prevWorkflowId,
    selectedFlowNode
  }) {
    const hasNewTheme = this.props.theme !== prevTheme;
    const hasNewWorkflowId = this.props.workflowId !== prevWorkflowId;
    const hasSelectedFlowNodeChanged =
      this.props.selectedFlowNode !== selectedFlowNode;

    if (hasNewTheme || hasNewWorkflowId) {
      hasNewWorkflowId && (await this.fetchAndSetWorkflowXML());
      return this.initViewer();
    }

    // In case only the selectedFlowNode changed.
    // This also means that the Viewer is already initiated so we can safely
    // call this.handleSelectedFlowNode.
    if (hasSelectedFlowNodeChanged) {
      this.handleSelectedFlowNode(
        this.props.selectedFlowNode,
        selectedFlowNode
      );
    }
  }

  async fetchAndSetWorkflowXML() {
    this.workflowXML = await api.fetchWorkflowXML(this.props.workflowId);
  }

  handleDiagramLoad = e => {
    if (e) {
      return console.log('Error rendering diagram:', e);
    }
    this.handleZoomReset();

    // in case onFlowNodesDetailsReady callback function is provided
    // call it with flowNodesDetails
    if (typeof this.props.onFlowNodesDetailsReady === 'function') {
      const elementRegistry = this.Viewer.get('elementRegistry');
      this.props.onFlowNodesDetailsReady(getFlowNodesDetails(elementRegistry));
    }

    if (this.props.selectableFlowNodes) {
      this.handleSelectableFlowNodes(this.props.selectableFlowNodes);
    }

    if (this.props.selectedFlowNode) {
      this.handleSelectedFlowNode(this.props.selectedFlowNode);
    }

    if (this.props.selectableFlowNodes && this.props.onFlowNodeSelected) {
      this.addEventListeners();
    }

    if (this.props.flowNodeStateOverlay) {
      this.addFlowNodeStateOverlay(this.props.flowNodeStateOverlay);
    }
  };

  initViewer = () => {
    // detach Viewer if it exists
    if (this.Viewer) {
      this.Viewer.detach();
    }

    // colors config for bpmnRenderer
    this.Viewer = new BPMNViewer({
      container: this.containerNode,
      bpmnRenderer: getDiagramColors(this.props.theme)
    });

    this.Viewer.importXML(this.workflowXML, this.handleDiagramLoad);
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

  handleSelectedFlowNode = (selectedFlowNode, prevSelectedFlowNode) => {
    // clear previously selected flow node marker is there is one
    if (prevSelectedFlowNode) {
      this.removeMarker(prevSelectedFlowNode, 'op-selected');
    }

    // add marker for newly selected flow node if there is one
    if (selectedFlowNode) {
      this.addMarker(selectedFlowNode, 'op-selected');
    }
  };

  handleElementClick = ({element = {}}) => {
    const {selectedFlowNode} = this.props;
    const isSelectableElement =
      this.props.selectableFlowNodes.filter(id => id === element.id).length > 0;

    // Only select the flownode if it's selectable and if it's not already selected.
    if (isSelectableElement && element.id !== selectedFlowNode) {
      this.props.onFlowNodeSelected(element.id);
      // if it's already selected, deselect it
    } else if (selectedFlowNode) {
      this.props.onFlowNodeSelected(null);
    }
  };

  addEventListeners = () => {
    const eventBus = this.Viewer.get('eventBus');
    eventBus.on('element.click', this.handleElementClick);
  };

  addFlowNodeStateOverlay = ({id, state}) => {
    const img = document.createElement('img');
    Object.assign(img, {
      src: iconMap[state][this.props.theme],
      width: 24,
      height: 24
    });
    const overlays = this.Viewer.get('overlays');
    overlays.add(id, {
      position: {bottom: 14, left: -10},
      html: img
    });
  };

  render() {
    return (
      <Styled.Diagram>
        <Styled.DiagramCanvas innerRef={this.containerRef} />
        <DiagramControls
          handleZoomIn={this.handleZoomIn}
          handleZoomOut={this.handleZoomOut}
          handleZoomReset={this.handleZoomReset}
        />
      </Styled.Diagram>
    );
  }
}

export default themed(Diagram);
