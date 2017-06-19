import {getHeatmap, addHeatmapOverlay} from './service';
import {removeOverlays} from 'utils';
import {isLoaded} from 'utils/loading';

export function createHeatmapRendererFunction(createTooltip) {
  return ({viewer}) => {
    let heatmap;
    let heatmapRendered = false;
    let data;
    let currentlyHovered = false;

    viewer.get('eventBus').on('element.hover', ({element: {id}}) => {
      if (currentlyHovered !== id) {
        currentlyHovered = id;
        removeOverlays(viewer);
        addHeatmapOverlay(viewer, id, data, createTooltip);
      }
    });

    return ({state, diagramRendered}) => {
      if (isLoaded(state.heatmap)) {
        if (diagramRendered) {
          renderHeatmap(state);
        }
      } else {
        heatmapRendered = false;
      }
    };

    function renderHeatmap({heatmap: {data: {flowNodes}}}) {
      data = flowNodes;

      // add heatmap
      if (!heatmapRendered) {
        removeHeatmap();
        heatmap = getHeatmap(viewer, flowNodes);
        viewer.get('canvas')._viewport.appendChild(heatmap);
        heatmapRendered = true;
      }
    }

    function removeHeatmap() {
      if (heatmap) {
        viewer.get('canvas')._viewport.removeChild(heatmap);
      }
    }
  };
}
