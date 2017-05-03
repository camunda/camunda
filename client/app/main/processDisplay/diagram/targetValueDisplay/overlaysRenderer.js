import {isBpmnType, removeOverlays} from 'utils';
import {prepareFlowNodes, addTargetValueBadge, addTargetValueTooltip, hoverElement, getTargetValue} from './service';
import {getHeatmap} from 'main/processDisplay/diagram/heatmap/service';

export function createOverlaysRenderer(State, TargetValueModal) {
  return ({viewer}) => {
    const canvas = viewer.get('canvas');
    const elementRegistry = viewer.get('elementRegistry');
    const eventBus = viewer.get('eventBus');

    let heatmap;

    eventBus.on('element.click', ({element}) => {
      if (isValidElement(element)) {
        TargetValueModal.open(element);
      }
    });

    eventBus.on('element.hover', ({element}) => {
      hoverElement(viewer, element);
    });

    function removeHighlight(element) {
      canvas.removeMarker(element, 'highlight');
    }

    function highlight(element) {
      if (element) {
        canvas.addMarker(element, 'highlight');
        const outline = elementRegistry.getGraphics(element).querySelector('.djs-outline');

        outline.setAttribute('rx', '14px');
        outline.setAttribute('ry', '14px');
      }
    }

    function markAsClickable(element) {
      canvas.addMarker(element, 'clickable');
    }

    function isValidElement(element) {
      return isBpmnType(element, ['Task', 'IntermediateCatchEvent', 'SubProcess', 'CallActivity', 'EventBasedGateway']);
    }

    return ({state:{heatmap:{data:{flowNodes}}, targetValue:{data}}, diagramRendered}) => {
      if (diagramRendered) {
        removeOverlays(viewer);

        elementRegistry.forEach((element) => {
          removeHighlight(element);
          if (isValidElement(element)) {
            markAsClickable(element);
            if (!getTargetValue(State, element)) {
              highlight(element);
            } else {
              addTargetValueBadge(viewer, element, getTargetValue(State, element), TargetValueModal.open);
              addTargetValueTooltip(viewer, element, flowNodes[element.businessObject.id], getTargetValue(State, element));
            }
          }
        });

        removeHeatmap(viewer);
        heatmap = getHeatmap(viewer, prepareFlowNodes(data, flowNodes));
        canvas._viewport.appendChild(heatmap);
      }
    };

    function removeHeatmap() {
      if (heatmap) {
        canvas._viewport.removeChild(heatmap);
      }
    }
  };
}
