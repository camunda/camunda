import {jsx, updateOnlyWhenStateChanges, withSelector} from 'view-utils';
import Viewer from 'bpmn-js/lib/NavigatedViewer';
import {getHeatmap, hoverElement,
        removeHeatmapOverlay, addHeatmapOverlay} from './diagram.service';
import {LOADED_STATE} from 'utils/loading';

export const Diagram = withSelector(DiagramComponent);

function DiagramComponent() {
  return <BpmnViewer />;
}

function BpmnViewer() {
  return (node) => {
    const viewer = new Viewer({
      container: node
    });
    let heatmap;
    let diagramRendered = false;
    let heatmapRendered = false;

    viewer.get('eventBus').on('element.hover', ({element}) => {
      hoverElement(element);
    });

    const update = (display) => {
      if (display.diagram.state === LOADED_STATE) {
        renderDiagram(display);
      }

      if (display.heatmap.state === LOADED_STATE) {
        renderHeatmap(display);
      }
    };

    function renderDiagram(display) {
      if (!diagramRendered) {
        viewer.importXML(display.diagram.data, (err) => {
          if (err) {
            node.innerHTML = `Could not load diagram, got error ${err}`;
          }
          diagramRendered = true;
          resetZoom(viewer);

          if (display.heatmap.state === LOADED_STATE) {
            renderHeatmap(display);
          }
        });
      }
    }

    function renderHeatmap({hovered, heatmap: {data}}) {
      if (diagramRendered) {
        // add heatmap
        if (!heatmapRendered) {
          removeHeatmap();
          heatmap = getHeatmap(viewer, data);
          viewer.get('canvas')._viewport.appendChild(heatmap);
          heatmapRendered = true;
        }

        // add heatmap overlays
        removeHeatmapOverlay(viewer);
        addHeatmapOverlay(viewer, data, hovered);
      }
    }

    function removeHeatmap() {
      if (heatmap) {
        viewer.get('canvas')._viewport.removeChild(heatmap);
      }
    }

    return updateOnlyWhenStateChanges(update);
  };
}

function resetZoom(viewer) {
  const canvas = viewer.get('canvas');

  canvas.resized();
  canvas.zoom('fit-viewport', 'auto');
}
