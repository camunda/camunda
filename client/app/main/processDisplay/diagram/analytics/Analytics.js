import {toggleEndEvent, toggleGateway, hoverElement, addBranchOverlay, showSelectedOverlay, isValidElement} from './service';
import {removeOverlays} from 'utils';
import {resetStatisticData} from 'main/processDisplay/statistics';

export function createCreateAnalyticsRendererFunction(integrator) {
  return function createAnalyticsRenderer({viewer}) {
    const canvas = viewer.get('canvas');
    const elementRegistry = viewer.get('elementRegistry');

    viewer.get('eventBus').on('element.hover', ({element}) => {
      // takes care of overlays
      hoverElement(viewer, element);

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

        removeOverlays(viewer);
        addBranchOverlay(viewer, data);

        Object.values(selection).forEach(element => {
          highlight(element, 'highlight_selected');
          showSelectedOverlay(viewer, element);
        });
      }
    };
  };
}
