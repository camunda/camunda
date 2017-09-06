import React from 'react';
import Viewer from 'bpmn-js/lib/Viewer';
import {resetZoom} from './Diagram';
import {Loader} from './Loader.react';
import {createQueue} from 'utils';

const jsx = React.createElement;

const queue = createQueue();

export class DiagramPreview extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      error: false
    };
  }

  componentDidMount() {
    this.viewer = new Viewer({
      container: this.container
    });

    if (this.props.loading) {
      this.loadDiagram();
    }
  }

  render() {
    // <Icon icon="alert" />
    // Icon usage is left here so it can be found be search while changeing Icon component to use React
    // TODO: change to Icon as soon as Icon component is reactified
    const error = <div className="diagram-error">
      <span className="glyphicon glyphicon-alert"></span>
      <div className="text">
        No diagram
      </div>
    </div>;

    const diagram = <div className="diagram__holder" style={{position: 'relative'}} ref={this.attachDiagramContainer}>
    </div>;

    return <div style={{position: 'relative', height: '100%', width: '100%'}}>
      <Loader visible={this.props.loading} className="diagram-loading" style={{position: 'absolute'}} />
      {this.state.error ? error : ''}
      {!this.state.error ? diagram : ''}
    </div>;
  }

  attachDiagramContainer = container => this.container = container;

  componentDidUpdate() {
    if (this.props.loading && this.props.diagram && this.viewer) {
      this.loadDiagram();
    }
  }

  loadDiagram() {
    const viewer = this.viewer;

    if (this.props.diagram) {
      this.lastDiagram = this.props.diagram;

      queue.addTask(done => {
        viewer.importXML(this.props.diagram, err => {
          if (err) {
            this.props.onLoaded();
            return this.setState({
              error: true
            });
          }

          this.setState({
            error: false
          });
          this.props.onLoaded();

          try {
            resetZoom(viewer);
          } catch (error) {
            // Do nothing it is fine. Sometimes reset zoom throws error in FF
            // But it seems like nothing brakes, so let's just ignore it
            // This is knows issue: https://github.com/bpmn-io/bpmn-js/issues/665
          } finally {
            done();
          }
        });
      });
    } else {
      this.props.onLoaded();
      this.setState({
        error: true
      });
    }
  }
}
