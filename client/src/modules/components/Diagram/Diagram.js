import React from 'react';
import PropTypes from 'prop-types';
import BPMNViewer from 'bpmn-js/lib/NavigatedViewer';

import * as Styled from './styled';
import DiagramControls from './DiagramControls';
import {Colors, themed, themeStyle} from 'modules/theme';
import * as api from './api';

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

  initViewer = () => {
    // detach Viewer if it exists
    if (this.Viewer) {
      this.Viewer.detach();
    }

    // colors config for bpmnRenderer
    const bpmnRenderer = {
      defaultFillColor: themeStyle({
        dark: Colors.uiDark02,
        light: Colors.uiLight04
      })(this.props),
      defaultStrokeColor: themeStyle({
        dark: Colors.darkDiagram,
        light: Colors.uiLight06
      })(this.props)
    };

    this.Viewer = new BPMNViewer({
      container: this.containerNode,
      bpmnRenderer
    });

    this.Viewer.importXML(this.workflowXML, e => {
      // TODO: Error should be handled correctly
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

  componentDidUpdate() {
    this.initViewer();
  }
}

export default themed(Diagram);
