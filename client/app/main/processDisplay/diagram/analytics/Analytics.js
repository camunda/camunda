import {setEndEvent, setGateway} from './service';
import {isBpmnType} from 'utils';
import {resetStatisticData} from 'main/processDisplay/statistics';

export function createAnalyticsRenderer({viewer}) {
  const canvas = viewer.get('canvas');
  const elementRegistry = viewer.get('elementRegistry');

  viewer.get('eventBus').on('element.click', ({element}) => {
    if (isBpmnType(element, 'EndEvent')) {
      resetStatisticData();
      setEndEvent(element);
    } else if (isBpmnType(element, 'Gateway')) {
      resetStatisticData();
      setGateway(element);
    }
  });

  function needsHighlight(element) {
    return isBpmnType(element, 'Gateway') || isBpmnType(element, 'EndEvent');
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

  return ({state: {selection}, diagramRendered}) => {
    if (diagramRendered) {
      elementRegistry.forEach((element) => {
        removeHighlight(element);
        if (needsHighlight(element)) {
          highlight(element);
        }
      });

      Object.values(selection).forEach(element => {
        highlight(element, 'highlight_selected');
      });
    }
  };
}
