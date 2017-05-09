import generateHeatmap from './generateHeatmap';
import {$document} from 'view-utils';

export const VALUE_OVERLAY = 'VALUE_OVERLAY';

export function addHeatmapOverlay(viewer, element, data, createTooltip) {
  const value = data[element];

  if (value !== undefined) {
    // create overlay node from html string
    const container = document.createElement('div');

    container.innerHTML =
    `<div class="tooltip top" role="tooltip" style="opacity: 1;">
    <div class="tooltip-arrow"></div>
    <div class="tooltip-inner" style="text-align: left;"></div>
    </div>`;
    const overlayHtml = container.firstChild;

    const tooltipContent = createTooltip(value);
    const tooltipContentNode = tooltipContent.nodeName ?
      tooltipContent:
      $document.createTextNode(tooltipContent);

    overlayHtml.querySelector('.tooltip-inner').appendChild(tooltipContentNode);

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

    // react to changes in the overlay content and reposition it
    const observer = new MutationObserver(() => {
      overlayHtml.parentNode.style.left = elementWidth / 2 - overlayHtml.clientWidth / 2 + 'px';
    });

    observer.observe(tooltipContentNode, {childList: true, subtree: true});

    // add overlay to viewer
    viewer.get('overlays').add(element, VALUE_OVERLAY, {
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
