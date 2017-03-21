import {getHeatmap, hoverElement, addHeatmapOverlay} from './service';
import {removeOverlays} from 'utils';
import {isLoaded} from 'utils/loading';

export function createHeatmapRenderer({viewer}) {
  let heatmap;
  let heatmapRendered = false;

  viewer.get('eventBus').on('element.hover', ({element}) => {
    hoverElement(viewer, element);
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
    // add heatmap
    if (!heatmapRendered) {
      removeHeatmap(viewer);
      heatmap = getHeatmap(viewer, flowNodes);
      viewer.get('canvas')._viewport.appendChild(heatmap);
      heatmapRendered = true;
    }

    // add heatmap overlays
    removeOverlays(viewer);
    addHeatmapOverlay(viewer, flowNodes);
  }

  function removeHeatmap() {
    if (heatmap) {
      viewer.get('canvas')._viewport.removeChild(heatmap);
    }
  }
}
