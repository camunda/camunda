import {jsx, updateOnlyWhenStateChanges, withSelector} from 'view-utils';
import Viewer from 'bpmn-js/lib/Viewer';
import {resetZoom} from './Diagram';

export const DiagramPreview = withSelector(() => {
  return <div className="diagram__holder" style="position: relative;">
    <BpmnViewer />
  </div>;
});

function BpmnViewer() {
  return (node, eventsBus) => {
    const viewer = new Viewer({
      container: node
    });

    let diagramRendered = false;

    const update = (diagram) => {
      if (!diagramRendered) {
        viewer.importXML(diagram, (err) => {
          if (err) {
            node.innerHTML = `Could not load diagram, got error ${err}`;
          }
          diagramRendered = true;
          resetZoom(viewer);
        });
      }
    };

    return updateOnlyWhenStateChanges(update);
  };
}
