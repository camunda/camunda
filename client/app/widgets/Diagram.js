import {jsx, updateOnlyWhenStateChanges, withSelector} from 'view-utils';
import Viewer from 'bpmn-js/lib/NavigatedViewer';
import {isLoaded} from 'utils/loading';

export function createDiagram() {
  const BpmnViewer = createBpmnViewer();
  const Diagram = withSelector(({createOverlaysRenderer}) => {
    return <div className="diagram__holder">
      <BpmnViewer createOverlaysRenderer={createOverlaysRenderer} />
    </div>;
  });

  Diagram.getViewer = BpmnViewer.getViewer;

  return Diagram;
}

function createBpmnViewer() {
  let viewer;

  const BpmnViewer = ({createOverlaysRenderer = []}) => {
    return (node, eventsBus) => {
      viewer = new Viewer({
        container: node
      });

      if (typeof createOverlaysRenderer === 'function') {
        createOverlaysRenderer = [createOverlaysRenderer];
      }

      const renderOverlays = createOverlaysRenderer.map(createFct => createFct({
        viewer,
        node,
        eventsBus
      }));
      let diagramRendered = false;

      const update = (display) => {
        if (isLoaded(display.diagram)) {
          renderDiagram(display);
        } else {
          diagramRendered = false;
        }

        renderOverlays.forEach(fct => fct({
          state: display,
          diagramRendered
        }));
      };

      function renderDiagram(display) {
        if (!diagramRendered) {
          viewer.importXML(display.diagram.data, (err) => {
            if (err) {
              node.innerHTML = `Could not load diagram, got error ${err}`;
            }
            diagramRendered = true;
            resetZoom(viewer);

            renderOverlays.forEach(fct => fct({
              state: display,
              diagramRendered
            }));
          });
        }
      }

      return updateOnlyWhenStateChanges(update);
    };
  };

  BpmnViewer.getViewer = () => {
    return viewer;
  };

  return BpmnViewer;
}

function resetZoom(viewer) {
  const canvas = viewer.get('canvas');

  canvas.resized();
  canvas.zoom('fit-viewport', 'auto');
}
