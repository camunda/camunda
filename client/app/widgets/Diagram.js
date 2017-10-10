import React from 'react';
const jsx = React.createElement;

import Viewer from 'bpmn-js/lib/NavigatedViewer';
import {isLoaded} from 'utils/loading';
import {Loader} from './Loader.react';

export function createDiagram() {
  const BpmnViewer = createBpmnViewer();

  class Diagram extends React.Component {
    constructor(props) {
      super(props);

      this.state = {
        loaded: false
      };
    }

    onLoaded = () => {
      this.setState({loaded: true});
    }

    render() {
      console.log('rendering diagram', this.state.loaded);
      return (
        <div>
          <Loader visible={!this.state.loaded} className="diagram-loading" style={{position: 'absolute'}} />
          <div className="diagram__holder">
            <BpmnViewer onLoaded={this.onLoaded} createOverlaysRenderer={this.props.createOverlaysRenderer} diagram={this.props} />
            <ZoomButton name="zoom in" icon="plus" listener={BpmnViewer.zoomIn} />
            <ZoomButton name="zoom out" icon="minus" listener={BpmnViewer.zoomOut} />
            <ZoomButton name="reset zoom" icon="screenshot" listener={BpmnViewer.resetZoom} />
          </div>
        </div>
      );
    }
  }

  Diagram.getViewer = BpmnViewer.getViewer;

  return Diagram;
}

function ZoomButton({name, listener, icon}) {
  return <button type="button" onClick={listener} title={name} className={'btn btn-default ' + name.replace(' ', '-')}>
    <span className={'glyphicon glyphicon-' + icon} aria-hidden="true" />
  </button>;
}

function createBpmnViewer() {
  let viewer;

  class BpmnViewer extends React.Component {
    constructor(props) {
      super(props);

      this.state = {
        err: null
      };
    }
    storeContainer = container => {
      this.container = container;
    }

    render() {
      return <div style={{width: '100%', height: '100%'}} ref={this.storeContainer}>
        {this.state.err && `Could not load diagram, got error ${this.state.err}`}
      </div>;
    }

    componentDidMount() {
      viewer = this.viewer = new Viewer({
        container: this.container,
        canvas: {
          deferUpdate: false
        }
      });

      let createOverlaysRenderer = this.props.createOverlaysRenderer || [];

      if (typeof createOverlaysRenderer === 'function') {
        createOverlaysRenderer = [createOverlaysRenderer];
      }

      this.renderOverlays = createOverlaysRenderer.map(createFct => createFct({
        viewer: this.viewer,
        node: this.container
      }));
      this.diagramRendered = false;

      this.renderDiagramIfLoaded();
    }

    renderDiagramIfLoaded() {
      const {diagram} = this.props;

      if (isLoaded(diagram.bpmnXml)) {
        this.renderDiagram(diagram);
      } else {
        this.diagramRendered = false;
      }

      this.renderOverlays.forEach(fct => fct({
        state: diagram,
        diagramRendered: this.diagramRendered
      }));
    }

    componentDidUpdate() {
      this.renderDiagramIfLoaded();
    }

    renderDiagram(diagram) {
      if (!this.diagramRendered) {
        this.viewer.importXML(diagram.bpmnXml.data, (err) => {
          if (err) {
            this.setState({err});
          }
          this.diagramRendered = true;
          resetZoom(viewer);

          this.renderOverlays.forEach(fct => fct({
            state: diagram,
            diagramRendered: this.diagramRendered
          }));

          this.props.onLoaded();
        });
      }
    }
  }

  BpmnViewer.getViewer = () => {
    return viewer;
  };

  BpmnViewer.zoomIn = () => {
    viewer.get('zoomScroll').zoom(0.5);
  };

  BpmnViewer.zoomOut = () => {
    viewer.get('zoomScroll').zoom(-0.5);
  };

  BpmnViewer.resetZoom = () => {
    resetZoom(viewer);
  };

  return BpmnViewer;
}

export function resetZoom(viewer) {
  const canvas = viewer.get('canvas');

  canvas.resized();
  canvas.zoom('fit-viewport', 'auto');
}
