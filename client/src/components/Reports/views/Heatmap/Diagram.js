import React from 'react';

import Viewer from 'bpmn-js/lib/NavigatedViewer';

export default class Diagram extends React.Component {
  constructor(props) {
    super(props);

    this.viewer = new Viewer({
      canvas: {
        deferUpdate: false
      }
    });

    this.state = {
      loaded: false
    };
  }

  storeContainer = container => {
    this.container = container;
  }

  render() {
    return <div style={{width: '100%', height: '400px'}} ref={this.storeContainer}>
      {this.state.loaded && this.props.children && React.cloneElement(this.props.children, { viewer: this.viewer })}
    </div>;
  }

  componentDidMount() {
    this.viewer.attachTo(this.container);

    this.viewer.importXML(this.props.xml, (err) => {
      const canvas = this.viewer.get('canvas');

      canvas.resized();
      canvas.zoom('fit-viewport', 'auto');

      this.setState({loaded: true});
    });
  }
}
