import {dispatchAction} from 'view-utils';
import generateHeatmap from './heatmap';
import {createHoverElementAction} from './reducer';

export function hoverElement(element) {
  dispatchAction(createHoverElementAction(element));
}

export function removeHeatmapOverlay(viewer) {
  viewer.get('overlays').clear();
}

export function addHeatmapOverlay(viewer, data, element) {
  const value = data[element];

  if (value !== undefined) {
    // create overlay node from html string
    const container = document.createElement('div');

    container.innerHTML =
    `<div class="tooltip top" role="tooltip" style="pointer-events: none; opacity: 1;">
      <div class="tooltip-arrow"></div>
      <div class="tooltip-inner" style="text-align: left;">${value}</div>
    </div>`;
    const overlayHtml = container.firstChild;

    // calculate overlay width
    document.body.appendChild(overlayHtml);
    const overlayWidth = overlayHtml.clientWidth;

    document.body.removeChild(overlayHtml);

    // calculate element width
    const elementWidth = parseInt(
      viewer
        .get('elementRegistry')
        .getGraphics(element)
        .querySelector('.djs-hit')
        .getAttribute('width')
    , 10);

    // add overlay to viewer
    viewer.get('overlays').add(element, {
      position: {
        top: -36,
        left: elementWidth / 2 - overlayWidth / 2
      },
      show: {
        minZoom: -Infinity,
        maxZoom: +Infinity
      },
      html: overlayHtml
    });
  }
}

export function getHeatmap(viewer, data) {
  const heat = generateHeatmap(viewer, data);
  const node = document.createElementNS('http://www.w3.org/2000/svg', 'image');

  Object.keys(heat.dimensions).forEach(prop => {
    node.setAttributeNS(null, prop, heat.dimensions[prop]);
  });

  node.setAttributeNS('http://www.w3.org/1999/xlink', 'xlink:href', heat.img);
  node.setAttributeNS(null, 'style', 'opacity: 0.8; pointer-events: none;');

  return node;
}
