import {dispatchAction} from 'view-utils';
import {updateOverlayVisibility, isBpmnType} from 'utils';
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

export function hoverElement(viewer, element) {
  updateOverlayVisibility(viewer, element, BRANCH_OVERLAY);
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

export function showSelectedOverlay(viewer, element) {
  viewer
    .get('overlays')
    .get({type: 'BRANCH_OVERLAY'})
    .filter(({element: {id}}) => {
      return id === element;
    })
    .forEach((node) => {
      node.keepOpen = true;
      node.html.style.display = 'block';
    });
}

export function addBranchOverlay(viewer, {flowNodes, piCount}) {
  const elementRegistry = viewer.get('elementRegistry');
  const overlays = viewer.get('overlays');

  Object.keys(flowNodes).forEach(element => {
    const value = flowNodes[element];

    if (value !== undefined && isBpmnType(elementRegistry.get(element), 'EndEvent')) {
      // create overlay node from html string
      const container = document.createElement('div');
      const percentageValue = Math.round(value / piCount * 1000) / 10;

      container.innerHTML =
      `<div class="tooltip top" role="tooltip" style="display: none; opacity: 1;">
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

      // add overlay to viewer
      overlays.add(element, BRANCH_OVERLAY, {
        position: {
          bottom: 0,
          right: 0
        },
        show: {
          minZoom: -Infinity,
          maxZoom: +Infinity
        },
        html: overlayHtml
      });
    }
  });
}
