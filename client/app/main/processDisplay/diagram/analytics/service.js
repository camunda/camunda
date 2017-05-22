import {dispatchAction} from 'view-utils';
import {isBpmnType} from 'utils';
import {createUnsetElementAction, createToggleElementAction} from './reducer';

export const BRANCH_OVERLAY = 'BRANCH_OVERLAY';

export function toggleEndEvent({id}) {
  dispatchAction(createToggleElementAction(id, 'endEvent'));
}

export function unsetEndEvent() {
  dispatchAction(createUnsetElementAction('endEvent'));
}

export function toggleGateway({id}) {
  dispatchAction(createToggleElementAction(id, 'gateway'));
}

export function unsetGateway() {
  dispatchAction(createUnsetElementAction('gateway'));
}

export function leaveGatewayAnalysisMode() {
  unsetGateway();
  unsetEndEvent();
}

export function isValidElement(element, type) {
  if (type === 'Gateway') {
    return isValidGateway(element);
  } else if (type === 'EndEvent') {
    return isValidEndEvent(element);
  }
}

function isValidGateway(element) {
  return isBpmnType(element, 'Gateway')  && element.businessObject.outgoing.length > 1;
}

function isValidEndEvent(element) {
  return isBpmnType(element, 'EndEvent');
}

export function addBranchOverlay(viewer, element, {flowNodes, piCount}) {
  if (!element || !isBpmnType(viewer.get('elementRegistry').get(element), 'EndEvent')) {
    return;
  }

  const overlays = viewer.get('overlays');
  const value = flowNodes[element] || 0;

  // create overlay node from html string
  const container = document.createElement('div');
  const percentageValue = Math.round(value / piCount * 1000) / 10;

  container.innerHTML =
  `<div class="tooltip top" role="tooltip" style="opacity: 1;">
    <table class="cam-table end-event-statistics">
      <tbody>
        <tr>
          <td>${piCount}</td>
          <td>Process Instances Total</td>
        </tr>
        <tr>
          <td>${value}</td>
          <td>Process Instances reached this state</td>
        </tr>
        <tr>
          <td>${percentageValue}%</td>
          <td>of Process Instances reached this state</td>
        </tr>
      </tbody>
    </table>
  </div>`;
  const overlayHtml = container.firstChild;

  // stop propagation of mouse event so that click+drag does not move canvas
  // when user tries to select text
  overlayHtml.addEventListener('mousedown', (evt) => {
    evt.stopPropagation();
  });

  // calculate overlay dimensions
  document.body.appendChild(overlayHtml);
  const overlayWidth = overlayHtml.clientWidth;
  const overlayHeight = overlayHtml.clientHeight;

  document.body.removeChild(overlayHtml);

  // calculate element dimensions
  const elementNode = viewer
    .get('elementRegistry')
    .getGraphics(element)
    .querySelector('.djs-hit');
  const elementWidth = parseInt(elementNode.getAttribute('width'), 10);
  const elementHeight = parseInt(elementNode.getAttribute('height'), 10);

  const position = {bottom: 0, right: 0};

  const viewbox = viewer.get('canvas').viewbox();
  const elementPosition = viewer.get('elementRegistry').get(element);

  if (elementPosition.x + elementWidth + overlayWidth > viewbox.x + viewbox.width) {
    position.right = undefined;
    position.left = -overlayWidth;
  }
  if (elementPosition.y + elementHeight + overlayHeight > viewbox.y + viewbox.height) {
    position.bottom = undefined;
    position.top = -overlayHeight;
  }

  // add overlay to viewer
  overlays.add(element, BRANCH_OVERLAY, {
    position,
    show: {
      minZoom: -Infinity,
      maxZoom: +Infinity
    },
    html: overlayHtml
  });
  // });
}
