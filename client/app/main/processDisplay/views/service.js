import {$document} from 'view-utils';

export function addDiagramTooltip(viewer, element, tooltipContent) {
  // create overlay node from html string
  const container = document.createElement('div');

  container.innerHTML =
  `<div class="tooltip" role="tooltip" style="opacity: 1;">
  <div class="tooltip-arrow"></div>
  <div class="tooltip-inner" style="text-align: left;"></div>
  </div>`;
  const overlayHtml = container.firstChild;

  const tooltipContentNode = tooltipContent.nodeName ?
    tooltipContent:
    $document.createTextNode(tooltipContent);

  overlayHtml.querySelector('.tooltip-inner').appendChild(tooltipContentNode);

  // calculate overlay width
  document.body.appendChild(overlayHtml);
  const overlayWidth = overlayHtml.clientWidth;
  const overlayHeight = overlayHtml.clientHeight;

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

  const position = {
    left: elementWidth / 2 - overlayWidth / 2
  };

  if (viewer.get('elementRegistry').get(element).y - viewer.get('canvas').viewbox().y < overlayHeight) {
    position.bottom = 0;
    overlayHtml.classList.add('bottom');
  } else {
    position.top = -overlayHeight;
    overlayHtml.classList.add('top');
  }

  // add overlay to viewer
  return viewer.get('overlays').add(element, {
    position,
    show: {
      minZoom: -Infinity,
      maxZoom: +Infinity
    },
    html: overlayHtml
  });
}
