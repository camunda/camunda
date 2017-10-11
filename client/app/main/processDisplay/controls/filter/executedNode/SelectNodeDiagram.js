import Viewer from 'bpmn-js/lib/NavigatedViewer';
import React from 'react';
import {resetZoom} from 'widgets';
import {Loader} from 'widgets/Loader.react';
import {onNextTick, isBpmnType} from 'utils';

const EXCLUDED_TYPES = [
  'Process', 'SequenceFlow', 'DataStoreReference', 'DataObjectReference',
  'Association', 'TextAnnotation', 'Participant', 'Collaboration',
  'DataInputAssociation', 'DataOutputAssociation'
];
const selectedMarker = 'highlight';
const jsx = React.createElement;

export class SelectNodeDiagram extends React.PureComponent {
  constructor(props) {
    super(props);

    this.state = {
      loading: true
    };
  }

  render() {
    const {loading} = this.state;

    return <div className="executed-node-diagram">
      <Loader className="diagram-loading" style={{position: 'absolute'}} visible={loading}>
      </Loader>
      <div className="diagram__holder" ref={this.saveContainer}>
      </div>
    </div>;
  }

  saveContainer = node => this.container = node;

  componentDidMount() {
    const {onSelectionChange} = this.props;

    this.viewer = new Viewer({
      container: this.container
    });

    this.canvas = this.viewer.get('canvas');
    this.elementRegistry = this.viewer.get('elementRegistry');

    this.viewer.on('element.click', ({element}) => {
      if (!isBpmnType(element, EXCLUDED_TYPES)) {
        if (this.canvas.hasMarker(element.id, selectedMarker)) {
          this.canvas.removeMarker(element.id, selectedMarker);
        } else {
          this.canvas.addMarker(element.id, selectedMarker);
        }

        onSelectionChange(
          this.elementRegistry
            .filter(element => {
              return this.canvas.hasMarker(element.id, selectedMarker);
            })
            .map(({id, businessObject}) => {
              return {
                id,
                name: businessObject && businessObject.name
              };
            })
        );
      }
    });

    if (this.props.diagramVisible) {
      this.loadDiagram(this.props.xml);
    }
  }

  componentWillReceiveProps({diagramVisible, xml}) {
    if (diagramVisible) {
      this.loadDiagram(xml);
    }
  }

  loadDiagram(xml) {
    if (!xml) {
      return;
    }

    if (this.diagramLoaded) {
      resetZoom(this.viewer);
      this.clearSelected();

      return;
    }

    this.viewer.importXML(xml, error => {
      if (error) {
        return this.finishLoading();
      }

      this.diagramLoaded = true;
      this.finishLoading();

      resetZoom(this.viewer);

      onNextTick(() => {
        this.elementRegistry.forEach(element => {
          if (!isBpmnType(element, EXCLUDED_TYPES)) {
            this.canvas.addMarker(element.id, 'element-selectable');
          }
        });

        this.clearSelected();
      }, 20);
    });
  }

  clearSelected() {
    this.elementRegistry.forEach(element => {
      this.canvas.removeMarker(element.id, selectedMarker);
    });
  }

  finishLoading() {
    if (this.viewer) {
      this.setState({
        loading: false
      });
    }
  }
}
