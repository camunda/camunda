import {toggleEndEvent, toggleGateway, addBranchOverlay, isValidElement} from './service';
import {removeOverlays} from 'utils';
import {resetStatisticData} from 'main/processDisplay/statistics';

export function createCreateAnalyticsRendererFunction(integrator) {
  return function createAnalyticsRenderer({viewer}) {
    const canvas = viewer.get('canvas');
    const elementRegistry = viewer.get('elementRegistry');
    let heatmapData;
    let elementSelection;
    let currentlyHovered = false;

    viewer.get('eventBus').on('element.hover', ({element}) => {
      // takes care of overlays
      if (currentlyHovered !== element.id && heatmapData) {
        currentlyHovered = element.id;
        removeOverlays(viewer);
        addBranchOverlay(viewer, element.id, heatmapData);

        if (elementSelection.endEvent) {
          addBranchOverlay(viewer, elementSelection.endEvent, heatmapData);
        }
      }

      // takes care of the controls integrator
      integrator.unhover('EndEvent');
      integrator.unhover('Gateway');
      if (isValidElement(element, 'EndEvent')) {
        integrator.hover('EndEvent');
      } else if (isValidElement(element, 'Gateway')) {
        integrator.hover('Gateway');
      }
    });

    viewer.get('eventBus').on('element.click', ({element}) => {
      if (isValidElement(element, 'EndEvent')) {
        resetStatisticData();
        toggleEndEvent(element);
      } else if (isValidElement(element, 'Gateway')) {
        resetStatisticData();
        toggleGateway(element);
      }
    });

    function needsHighlight(element) {
      return isValidElement(element, 'Gateway') || isValidElement(element, 'EndEvent');
    }

    function removeHighlight(element) {
      canvas.removeMarker(element, 'highlight');
      canvas.removeMarker(element, 'highlight_selected');
    }

    function highlight(element, type = 'highlight') {
      if (element) {
        canvas.addMarker(element, type);
        const outline = elementRegistry.getGraphics(element).querySelector('.djs-outline');

        outline.setAttribute('rx', '14px');
        outline.setAttribute('ry', '14px');
      }
    }

    return ({state: {selection, heatmap: {data}}, diagramRendered}) => {
      if (diagramRendered) {
        elementRegistry.forEach((element) => {
          removeHighlight(element);
          if (needsHighlight(element)) {
            highlight(element);
          }
        });

        heatmapData = data;
        elementSelection = selection;
        removeOverlays(viewer);

        Object.values(selection).forEach(element => {
          highlight(element, 'highlight_selected');
          addBranchOverlay(viewer, element, data);
        });
      }
    };
  };
}
