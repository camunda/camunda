import React from 'react';

import Viewer from 'bpmn-js/lib/NavigatedViewer';

import './BPMNDiagram.css';

export default class BPMNDiagram extends React.Component {
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
    return <div className='BPMNDiagram' style={this.props.style} ref={this.storeContainer}>
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

    const dashboardObject = this.container.closest('.DashboardObject');
    if(dashboardObject) {
      // if the diagram is on a dashboard, react to changes of the dashboard objects size
      new MutationObserver(() => {
        this.viewer.get('canvas').zoom('fit-viewport');
      }).observe(dashboardObject, {attributes: true});
    }
  }
}
