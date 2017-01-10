import {jsx, updateOnlyWhenStateChanges} from 'view-utils';
import Viewer from 'bpmn-js/lib/NavigatedViewer';
import {getHeatmap} from './diagram.service';
import {getDiagramXml} from './diagramBackend.mock';

export function Diagram() {
  return <div className="diagram">
    <div className="diagram__holder">
      <BpmnViewer />
    </div>
  </div>;
}

function BpmnViewer() {
  return (node) => {
    const viewer = new Viewer({
      container: node
    });
    let lastDiagram;
    let heatmap;
    let loaded = false;

    const update = ({diagram, filters}) => {
      if (lastDiagram !== diagram) {
        getDiagramXml(diagram).then(xml => {
          viewer.importXML(xml, (err) => {
            if (err) {
              node.innerHTML = `Could not load diagram, got error ${err}`;
            }

            resetZoom(viewer);
            loaded = true;
            updateHeatmap(diagram, filters);
          });

          lastDiagram = diagram;
        });
      } else if (loaded) {
        updateHeatmap(diagram, filters);
      }
    };

    return updateOnlyWhenStateChanges(update);

    function updateHeatmap(diagram, {startDate, endDate}) {

      removeHeatmap();

      getHeatmap(viewer, diagram).then(newHeatmap => {
        viewer.get('canvas')._viewport.appendChild(newHeatmap);
        heatmap = newHeatmap;
      });

    }

    function removeHeatmap() {
      if (heatmap) {
        viewer.get('canvas')._viewport.removeChild(heatmap);
      }
    }
  };
}

function resetZoom(viewer) {
  const canvas = viewer.get('canvas');

  canvas.resized();
  canvas.zoom('fit-viewport', 'auto');
}
