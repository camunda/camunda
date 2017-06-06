import {
  toggleEndEvent, toggleGateway, addBranchOverlay, isValidElement,
  removeHighlights, addHighlight
} from './service';
import {removeOverlays} from 'utils';
import {resetStatisticData} from 'main/processDisplay/statistics';

export function createCreateAnalyticsRendererFunction() {
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
        addBranchOverlay(viewer, element.id, heatmapData, true);

        if (elementSelection.EndEvent) {
          addBranchOverlay(viewer, elementSelection.EndEvent, heatmapData, false);
        }
      }

      if (elementRegistry.getAll().some(element => canvas.hasMarker(element, 'hover-highlight'))) {
        removeHighlights();
      }

      if (isValidElement(element, 'EndEvent')) {
        addHighlight('EndEvent', element.id);
      } else if (isValidElement(element, 'Gateway')) {
        addHighlight('Gateway', element.id);
      }
    });

    viewer.get('eventBus').on('element.click', ({element}) => {
      if (isValidElement(element, 'EndEvent')) {
        removeOverlays(viewer);
        toggleElement(element, 'EndEvent');
      } else if (isValidElement(element, 'Gateway')) {
        toggleElement(element, 'Gateway');
      }
    });

    function toggleElement(element, type) {
      resetStatisticData();

      if (type === 'Gateway') {
        toggleGateway(element);
      } else if (type === 'EndEvent') {
        toggleEndEvent(element);
      }
    }

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

    function isHovered(hover, element) {
      return Object
        .values(hover)
        .some(({elementType, elementId}) => {
          return elementId === element.id || !elementId && isValidElement(element, elementType);
        });
    }

    function isSelected(selection, element) {
      return Object
        .values(selection)
        .some(id => id === element.id);
    }

    function updateElementsHighlights(type, predicate, additionalActions) {
      elementRegistry.forEach(element => {
        const isHighlitNeeded = predicate(element);

        if (isHighlitNeeded) {
          canvas.addMarker(element, type);
        } else {
          canvas.removeMarker(element, type);
        }

        if (typeof additionalActions === 'function') {
          additionalActions(element, isHighlitNeeded);
        }
      });
    }

    return ({state: {analytics: {selection, hover}, heatmap: {data}}, diagramRendered}) => {
      if (diagramRendered) {
        // It might be not obviously clear why it is here.
        // Seems like a thing that should not be triggered at each update,
        // but it is necessary to avoid selection highlight flickering.
        elementRegistry.forEach(element => {
          removeHighlight(element);

          if (needsHighlight(element)) {
            highlight(element);
          }
        });

        heatmapData = data;
        elementSelection = selection;

        removeOverlays(viewer);

        updateElementsHighlights('highlight_selected', isSelected.bind(null, selection), (element, isHighlitNeeded) => {
          if (isHighlitNeeded) {
            addBranchOverlay(viewer, element.id, data, false);
          }
        });
        updateElementsHighlights('hover-highlight', isHovered.bind(null, hover), (element, isHighlitNeeded) => {
          if (isHighlitNeeded) {
            if (hover.EndEvent && hover.EndEvent.elementId === element.id) {
              addBranchOverlay(viewer, element.id, data, true);
            }
          }
        });
      }
    };
  };
}
