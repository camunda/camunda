import Viewer from 'bpmn-js/lib/NavigatedViewer';
import {jsx, createReferenceComponent, setElementVisibility, noop} from 'view-utils';
import {resetZoom, Loader} from 'widgets';
import {onNextTick, isBpmnType} from 'utils';

const EXCLUDED_TYPES = [
  'Process', 'SequenceFlow', 'DataStoreReference', 'DataObjectReference',
  'Association', 'TextAnnotation', 'Participant', 'Collaboration',
  'DataInputAssociation', 'DataOutputAssociation'
];
const selectedMarker = 'highlight';

export const createSelectedNodeDiagram = () => {
  const Reference = createReferenceComponent();
  let viewer;
  let canvas;
  let elementRegistry;
  let diagramLoaded = false;

  function SelectNodeDiagram({onSelectionChange = noop}) {
    return (node, eventsBus) => {
      const template = <div className="executed-node-diagram">
        <Loader className="diagram-loading" style="position: absolute">
          <Reference name="loader" />
        </Loader>
        <div className="diagram__holder">
          <Reference name="container" />
        </div>
      </div>;
      const update = template(node, eventsBus);

      viewer = new Viewer({
        container: Reference.getNode('container'),
        canvas: {
          deferUpdate: false
        }
      });
      canvas = viewer.get('canvas');
      elementRegistry = viewer.get('elementRegistry');

      viewer.on('element.click', ({element}) => {
        if (!isBpmnType(element, EXCLUDED_TYPES)) {
          if (canvas.hasMarker(element.id, selectedMarker)) {
            canvas.removeMarker(element.id, selectedMarker);
          } else {
            canvas.addMarker(element.id, selectedMarker);
          }

          onSelectionChange(
            elementRegistry
              .filter(element => {
                return canvas.hasMarker(element.id, selectedMarker);
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

      diagramLoaded = false;

      return [
        update
      ];
    };
  }

  function clearSelected() {
    elementRegistry.forEach(element => {
      canvas.removeMarker(element.id, selectedMarker);
    });
  }

  SelectNodeDiagram.loadDiagram = (xml, selected) => {
    if (diagramLoaded) {
      resetZoom(viewer);
      clearSelected();

      return;
    }

    viewer.importXML(xml, error => {
      if (error) {
        return;
      }

      diagramLoaded = true;
      const loaderNode = Reference.getNode('loader');

      setElementVisibility(loaderNode, false);

      window.resetModalZoom = resetZoom.bind(null, viewer);
      resetZoom(viewer);

      onNextTick(() => {
        elementRegistry.forEach(element => {
          if (!isBpmnType(element, EXCLUDED_TYPES)) {
            canvas.addMarker(element.id, 'element-selectable');
          }
        });

        clearSelected();
      }, 20);
    });
  };

  return SelectNodeDiagram;
};
