import {jsx, updateOnlyWhenStateChanges, Match, Case, withSelector} from 'view-utils';
import Viewer from 'bpmn-js/lib/NavigatedViewer';
import {loadHeatmap, loadDiagram, getHeatmap, hoverElement,
        removeHeatmapOverlay, addHeatmapOverlay} from './diagram.service';
import {LOADED_STATE, INITIAL_STATE} from './diagram.reducer';

export const Diagram = withSelector(DiagramComponent);

function DiagramComponent() {
  return <div className="diagram">
    <div className="diagram__holder">
      <BpmnViewer />
      <Match>
        <Case predicate={isLoading}>
          <div className="loading_indicator">
            <div className="spinner"><span className="glyphicon glyphicon-refresh spin"></span></div>
            <div className="text">loading</div>
          </div>
        </Case>
      </Match>
    </div>
  </div>;

  function isLoading({state, heatmap}) {
    return state !== LOADED_STATE || heatmap.state !== LOADED_STATE;
  }
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

    const update = (diagram) => {
      if (diagram.state === INITIAL_STATE) {
        loadDiagram(diagram);
      } else if (diagram.state === LOADED_STATE) {
        renderDiagram(diagram);
      }

      if (diagram.heatmap.state === INITIAL_STATE) {
        loadHeatmap(diagram);
      } else if (diagram.heatmap.state === LOADED_STATE) {
        renderHeatmap(diagram);
      }
    };

    function renderDiagram(diagram) {
      if (!diagramRendered) {
        viewer.importXML(diagram.xml, (err) => {
          if (err) {
            node.innerHTML = `Could not load diagram, got error ${err}`;
          }
          diagramRendered = true;
          resetZoom(viewer);

          if (diagram.heatmap.state === LOADED_STATE) {
            renderHeatmap(diagram);
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
