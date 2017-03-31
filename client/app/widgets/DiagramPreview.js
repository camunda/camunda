import {jsx, updateOnlyWhenStateChanges, withSelector,  createReferenceComponent, dispatchAction} from 'view-utils';
import Viewer from 'bpmn-js/lib/Viewer';
import {resetZoom} from './Diagram';
import {createQueue, runOnce} from 'utils';

const queue = createQueue();

export const DiagramPreview = withSelector(() => {
  const Reference = createReferenceComponent();

  const template = <div style="position: relative; height: 100%; width: 100%">
    <div className="loading_indicator overlay diagram-preview-loading" style="position:absolute;">
      <div className="spinner"><span className="glyphicon glyphicon-refresh spin"></span></div>
      <div className="text">loading</div>
      <Reference name="loader" />
    </div>
    <div className="diagram__holder" style="position: relative;">
      <Reference name="viewer" />
    </div>
  </div>;

  return (node, eventsBus) => {
    const templateUpdate = template(node, eventsBus);
    const viewerNode = Reference.getNode('viewer');
    const loaderNode = Reference.getNode('loader');

    const viewer = new Viewer({
      container: viewerNode
    });

    const update = runOnce((diagram) => {
      queue.addTask((done) => {
        viewer.importXML(diagram, (err) => {
          if (err) {
            viewerNode.innerHTML = `Could not load diagram, got error ${err}`;
          }

          resetZoom(viewer);
          dispatchAction({type: '@@LOADED_DIAGRAM'});
          done();
          loaderNode.style.display = 'none';
        });
      });
    });

    return [templateUpdate, updateOnlyWhenStateChanges(update)];
  };
});
