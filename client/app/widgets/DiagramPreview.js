import {jsx, updateOnlyWhenStateChanges, withSelector, $window, createReferenceComponent, dispatchAction} from 'view-utils';
import Viewer from 'bpmn-js/lib/Viewer';
import {resetZoom} from './Diagram';
import {LoadingIndicator} from './LoadingIndicator';
import md5 from 'md5';
import {createQueue, runOnce} from 'utils';

const queue = createQueue();
const {localStorage} = $window;

export const DiagramPreview = withSelector(() => {
  const Reference = createReferenceComponent();
  let isLoading = () => true;

  const template = <div style="position: relative; height: 100%; width: 100%">
    <LoadingIndicator floating predicate={() => isLoading()} />
    <div className="diagram__holder" style="position: relative;">
      <Reference name="viewer" />
    </div>
  </div>;

  return (node, eventsBus) => {
    const templateUpdate = template(node, eventsBus);
    const viewerNode = Reference.getNode('viewer');

    const viewer = new Viewer({
      container: viewerNode
    });

    const update = runOnce((diagram) => {
      const cacheKey = `diagram_${md5(diagram)}`;
      const cachedSVG = localStorage.getItem(cacheKey);

      if (cachedSVG) {
        viewerNode.innerHTML = cachedSVG;
        isLoading = () => false;
        dispatchAction({type: '@@LOADED_DIAGRAM'});

        return;
      }

      isLoading = queue.addTask(() => {
        viewer.importXML(diagram, (err) => {
          if (err) {
            viewerNode.innerHTML = `Could not load diagram, got error ${err}`;
          }

          resetZoom(viewer);

          localStorage.setItem(cacheKey, viewerNode.innerHTML);
          dispatchAction({type: '@@LOADED_DIAGRAM'});
        });
      });
    });

    return [templateUpdate, updateOnlyWhenStateChanges(update)];
  };
});
