import {getHeatmap, hoverElement,
        removeHeatmapOverlay, addHeatmapOverlay} from './service';
import {withSelector, jsx} from 'view-utils';
import {isLoaded} from 'utils/loading';
import {Diagram} from 'widgets/Diagram';
import {createAnalyticsRenderer} from './analytics';

export const HeatmapDiagram = withSelector(() =>
  <Diagram createOverlaysRenderer={[createHeatmapRenderer, createAnalyticsRenderer]} />
);

function createHeatmapRenderer({viewer}) {
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

  function renderHeatmap({heatmap: {data}}) {
    // add heatmap
    if (!heatmapRendered) {
      removeHeatmap(viewer);
      heatmap = getHeatmap(viewer, data);
      viewer.get('canvas')._viewport.appendChild(heatmap);
      heatmapRendered = true;
    }

    // add heatmap overlays
    removeHeatmapOverlay(viewer);
    addHeatmapOverlay(viewer, data);
  }

  function removeHeatmap() {
    if (heatmap) {
      viewer.get('canvas')._viewport.removeChild(heatmap);
    }
  }
}
