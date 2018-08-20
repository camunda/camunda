import React from 'react';
import PropTypes from 'prop-types';
import BPMNViewer from 'bpmn-js/lib/NavigatedViewer';

import {themed} from 'modules/theme';

import * as Styled from './styled';
import DiagramControls from './DiagramControls';
import * as api from 'modules/api/diagram';
import {getDiagramColors, getFlowNodesDetails} from './service';

class Diagram extends React.Component {
  static propTypes = {
    theme: PropTypes.string.isRequired,
    workflowId: PropTypes.string.isRequired,
    // callback function called when flowNodesDetails is ready
    onFlowNodesDetailsReady: PropTypes.func
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

  async componentDidUpdate({theme: prevTheme, workflowId: prevWorkflowId}) {
    const hasNewTheme = this.props.theme !== prevTheme;
    const hasNewWorkflowId = this.props.workflowId !== prevWorkflowId;

    if (hasNewTheme || hasNewWorkflowId) {
      hasNewWorkflowId && (await this.fetchAndSetWorkflowXML());
      this.initViewer();
    }
  }

  async fetchAndSetWorkflowXML() {
    this.workflowXML = await api.fetchWorkflowXML(this.props.workflowId);
  }

  onDiagramLoaded = e => {
    if (e) {
      return console.log('Error rendering diagram:', e);
    }
    this.handleZoomReset();

    // in case onFlowNodesDetailsReady callback function is provided
    // call it with flowNodesDetails
    const {onFlowNodesDetailsReady} = this.props;
    if (typeof onFlowNodesDetailsReady === 'function') {
      const elementRegistry = this.Viewer.get('elementRegistry');
      onFlowNodesDetailsReady(getFlowNodesDetails(elementRegistry));
    }
  };

  initViewer = () => {
    // detach Viewer if it exists
    if (this.Viewer) {
      this.Viewer.detach();
    }

    // colors config for bpmnRenderer
    const {theme} = this.props;
    this.Viewer = new BPMNViewer({
      container: this.containerNode,
      bpmnRenderer: getDiagramColors(theme)
    });

    this.Viewer.importXML(this.workflowXML, this.onDiagramLoaded);
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
