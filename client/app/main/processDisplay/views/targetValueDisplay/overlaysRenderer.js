import {isBpmnType, removeOverlays} from 'utils';
import {prepareFlowNodes, addTargetValueBadge, addTargetValueTooltip, getTargetValue} from './service';
import {getHeatmap} from 'main/processDisplay/views/frequency';

export function createOverlaysRenderer(onClick) {
  return ({viewer}) => {
    const canvas = viewer.get('canvas');
    const elementRegistry = viewer.get('elementRegistry');
    const eventBus = viewer.get('eventBus');

    let heatmap;
    let flowNodeData;
    let targetValueData;
    let currentlyHovered = false;
    let displayedTooltip;

    eventBus.on('element.click', ({element}) => {
      if (isValidElement(element)) {
        onClick(element);
      }
    });

    eventBus.on('element.hover', ({element}) => {
      if (currentlyHovered !== element.id) {
        currentlyHovered = element.id;

        if (displayedTooltip) {
          viewer.get('overlays').remove(displayedTooltip);
        }

        if (getTargetValue(targetValueData, element)) {
          displayedTooltip = addTargetValueTooltip(viewer, element, flowNodeData[element.businessObject.id], getTargetValue(targetValueData, element));
        }
      }
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
      flowNodeData = flowNodes;
      targetValueData = data;

      if (diagramRendered) {
        removeOverlays(viewer);

        elementRegistry.forEach((element) => {
          removeHighlight(element);
          if (isValidElement(element)) {
            markAsClickable(element);
            if (!getTargetValue(data, element)) {
              highlight(element);
            } else {
              addTargetValueBadge(viewer, element, getTargetValue(data, element), onClick);
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
