import React from 'react';
import PropTypes from 'prop-types';
import BPMNViewer from 'bpmn-js/lib/NavigatedViewer';

import {themed} from 'modules/theme';

import * as Styled from './styled';
import DiagramControls from './DiagramControls';
import * as api from 'modules/api/diagram';
import {getDiagramColors} from './service';

class Diagram extends React.Component {
  static propTypes = {
    theme: PropTypes.string.isRequired,
    workflowId: PropTypes.string.isRequired
  };

  constructor(props) {
    super(props);
    this.containerNode = null;
    this.Viewer = null;
    this.workflowXML = null;
  }

  async componentDidMount() {
    this.workflowXML = await api.workflowXML(this.props.workflowId);
    this.initViewer();
  }

  componentDidUpdate({theme: prevTheme, workflowId: prevWorkflowId}) {
    const {theme, workflowId} = this.props;
    if (theme !== prevTheme || workflowId !== prevWorkflowId) {
      this.initViewer();
    }
  }

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

    this.Viewer.importXML(this.workflowXML, e => {
      if (e) {
        return console.log('Error rendering diagram:', e);
      }
      this.handleZoomReset();
    });
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
