import {jsx, updateOnlyWhenStateChanges} from 'view-utils';
import Viewer from 'bpmn-js/lib/NavigatedViewer';

const dayInMs = 24 * 60 * 60 * 1000;

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
    let loaded = false;
    let lastOverlays = [];

    const update = ({diagram, filters}) => {
      if (lastDiagram !== diagram) {
        viewer.importXML(diagram, (err) => {
          if (err) {
            node.innerHTML = `Could not load diagram, got error ${err}`;
          }

          resetZoom(viewer);
          loaded = true;
          updateOverlays(filters);
        });

        lastDiagram = diagram;
      } else if (loaded) {
        updateOverlays(filters);
      }
    };

    return updateOnlyWhenStateChanges(update);

    function updateOverlays({startDate, endDate}) {
      const overlays = viewer.get('overlays');
      const diff = endDate && startDate ? (endDate - startDate) / dayInMs : 10;

      lastOverlays.forEach(overlay =>
        overlays.remove(overlay)
      );

      lastOverlays = viewer
        .get('elementRegistry')
        .filter(({type}) => type === 'bpmn:Task')
        .map(({id}) => {
          overlays.add(id, {
            position: {
              bottom: 0,
              right: 0
            },
            show: {
              minZoom: -Infinity,
              maxZoom: +Infinity
            },
            html: `<div class="diagram__overlay">${Math.round(diff * Math.random())}</div>`
          });

          return id;
        });
    }
  };
}

function resetZoom(viewer) {
  const canvas = viewer.get('canvas');

  canvas.resized();
  canvas.zoom('fit-viewport', 'auto');
}
