import {jsx, updateOnlyWhenStateChanges, Match, Case} from 'view-utils';
import Viewer from 'bpmn-js/lib/NavigatedViewer';
import {loadHeatmap, loadDiagram, getHeatmap} from './diagram.service';

export function Diagram() {
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

  function isLoading({diagram}) {
    return diagram.state !== 'LOADED' || diagram.heatmap.state !== 'LOADED';
  }
}

function BpmnViewer() {
  return (node) => {
    const viewer = new Viewer({
      container: node
    });
    let heatmap;
    let imported = false;

    const update = ({diagram}) => {
      if (diagram.state === 'INITIAL') {
        loadDiagram(diagram);
      } else if (diagram.state === 'LOADED') {
        if (imported) {
          updateHeatmap(diagram);
        } else {
          viewer.importXML(diagram.xml, (err) => {
            if (err) {
              node.innerHTML = `Could not load diagram, got error ${err}`;
            }
            imported = true;
            resetZoom(viewer);
            updateHeatmap(diagram);
          });
        }
      }
    };

    return updateOnlyWhenStateChanges(update);

    function updateHeatmap(diagram) {
      removeHeatmap();

      const state = diagram.heatmap.state;

      if (state === 'INITIAL') {
        loadHeatmap(diagram);
      } else if (state == 'LOADED') {
        heatmap = getHeatmap(viewer, diagram.heatmap.data);
        viewer.get('canvas')._viewport.appendChild(heatmap);
      }
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
