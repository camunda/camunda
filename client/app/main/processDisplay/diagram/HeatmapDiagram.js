import {getHeatmap, hoverElement,
        removeHeatmapOverlay, addHeatmapOverlay} from './service';
import {withSelector, jsx} from 'view-utils';
import {isLoaded} from 'utils/loading';
import {Diagram} from 'widgets/Diagram';

export const HeatmapDiagram = withSelector(() =>
  <Diagram createOverlaysRenderer={createHeatmapRenderer} />
);

function createHeatmapRenderer({viewer}) {
  let heatmap;
  let heatmapRendered = false;

  viewer.get('eventBus').on('element.hover', ({element}) => {
    hoverElement(element);
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

  function renderHeatmap({hovered, heatmap: {data}}) {
    // add heatmap
    if (!heatmapRendered) {
      removeHeatmap(viewer);
      heatmap = getHeatmap(viewer, data);
      viewer.get('canvas')._viewport.appendChild(heatmap);
      heatmapRendered = true;
    }

    // add heatmap overlays
    removeHeatmapOverlay(viewer);
    addHeatmapOverlay(viewer, data, hovered);
  }

  function removeHeatmap() {
    if (heatmap) {
      viewer.get('canvas')._viewport.removeChild(heatmap);
    }
  }
}
