import React from 'react';
import PropTypes from 'prop-types';
import BPMNViewer from 'bpmn-js/lib/NavigatedViewer';

import * as Styled from './styled';
import DiagramControls from './DiagramControls';
import {Colors, themed, themeStyle} from 'modules/theme';
import {getXML} from './api';

class Diagram extends React.Component {
  static propTypes = {
    theme: PropTypes.string.isRequired
  };

  containerNode = null;
  Viewer = null;

  async componentDidMount() {
    const xml = await getXML();

    // colors config for bpmnRenderer
    const defaultFillColor = themeStyle({
      dark: Colors.uiDark02,
      light: Colors.uiLight04
    })(this.props);
    const defaultStrokeColor = themeStyle({
      dark: Colors.darkDiagram,
      light: Colors.uiLight06
    })(this.props);
    const bpmnRenderer = {
      defaultFillColor,
      defaultStrokeColor
    };

    this.Viewer = new BPMNViewer({
      container: this.containerNode,
      bpmnRenderer
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
