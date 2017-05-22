import {jsx, updateOnlyWhenStateChanges, withSelector,  createReferenceComponent} from 'view-utils';
import Viewer from 'bpmn-js/lib/Viewer';
import {resetZoom} from './Diagram';
import {Loader} from './Loader';
import {createQueue} from 'utils';
import isEqual from 'lodash.isequal';

const queue = createQueue();

export function createDiagramPreview() {
  const Reference = createReferenceComponent();

  const DiagramPreview = withSelector(() => {
    return (node, eventsBus) => {
      const template = <div style="position: relative; height: 100%; width: 100%">
        <Loader className="diagram-loading" style="position: absolute">
          <Reference name="loader" />
        </Loader>
        <div className="diagram__holder" style="position: relative;">
          <Reference name="viewer" />
        </div>
      </div>;

      const templateUpdate = template(node, eventsBus);
      const viewerNode = Reference.getNode('viewer');
      const loaderNode = Reference.getNode('loader');

      const viewer = new Viewer({
        container: viewerNode
      });

      const update = (diagram) => {
        queue.addTask((done) => {
          viewer.importXML(diagram, (err) => {
            DiagramPreview.setLoading(false);

            if (err) {
              viewerNode.innerHTML = `Could not load diagram, got error ${err}`;
            }

            resetZoom(viewer);
            done();
          });
        });
      };

      function updatePreventionCondition(previousState, newState) {
        return loaderNode.style.display === 'none' && isEqual(previousState, newState);
      }

      return [templateUpdate, updateOnlyWhenStateChanges(update, updatePreventionCondition)];
    };
  });

  DiagramPreview.setLoading = (loading) => {
    const viewerNode = Reference.getNode('viewer');
    const loaderNode = Reference.getNode('loader');

    loaderNode.style.display = loading ? 'block' : 'none';
    viewerNode.style.display = loading ? 'none' : 'block';
  };

  return DiagramPreview;
}
