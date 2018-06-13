import React from 'react';
import BPMNViewer from 'bpmn-js/lib/NavigatedViewer';

import * as Styled from './styled';
import DiagramControls from './DiagramControls';
import {Colors} from 'modules/theme';
import {getXML} from './api';

export default class Diagram extends React.Component {
  containerNode = null;
  Viewer = null;

  async componentDidMount() {
    const xml = await getXML();
    this.Viewer = new BPMNViewer({
      container: this.containerNode,
      bpmnRenderer: {
        defaultFillColor: Colors.uiDark02,
        defaultStrokeColor: '#dedede'
      }
    });
    this.Viewer.importXML(xml, e => {
      if (e) {
        return console.log('oops error importing: ', e);
      }
      this.handleZoomReset();
    });
  }

  containerRef = node => {
    this.containerNode = node;
  };

  handleZoomIn = () => {
    this.Viewer.get('zoomScroll').stepZoom(0.1);
  };

  handleZoomOut = () => {
    this.Viewer.get('zoomScroll').stepZoom(-0.1);
  };

  handleZoomReset = () => {
    const canvas = this.Viewer.get('canvas');
    canvas.zoom('fit-viewport', 'auto');
  };

  render() {
    return (
      <Styled.Diagram innerRef={this.containerRef}>
        <DiagramControls
          handleZoomIn={this.handleZoomIn}
          handleZoomOut={this.handleZoomOut}
          handleZoomReset={this.handleZoomReset}
        />
      </Styled.Diagram>
    );
  }
}
