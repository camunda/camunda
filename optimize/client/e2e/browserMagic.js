/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ClientFunction} from 'testcafe';

export const addAnnotation = ClientFunction((selector, text, options = {x: 0, y: 50}) => {
  const el = selector();

  const bb = el.getBoundingClientRect();

  const textBox = document.createElement('div');
  textBox.style.padding = '15px 30px';
  textBox.style.backgroundColor = '#dcecff';
  textBox.style.border = '2px solid black';
  textBox.style.position = 'absolute';
  textBox.style.zIndex = '9999999';
  textBox.style.fontSize = '18px';
  textBox.style.whiteSpace = 'pre-wrap';
  textBox.style.textAlign = 'center';
  textBox.classList.add('SCREENSHOT__ANNOTATION');
  textBox.textContent = text;

  document.body.appendChild(textBox);

  // This calculates the positioning of the text box and the arrow
  // left, top: css properties of the textbox
  // c[x/y][s/e]: [s]tart/[e]nd [c]oordinates for [x]- and [y] axis of the svg arrow
  let left, top, cxs, cxe, cys, cye;
  if (Math.abs(options.x) > Math.abs(options.y)) {
    cys = bb.y + bb.height / 2;
    cye = cys + options.y;
    top = cye - textBox.clientHeight / 2;
    if (options.x > 0) {
      // right
      cxs = bb.x + bb.width;
      left = cxs + options.x;
    } else {
      // left
      cxs = bb.x;
      left = bb.x - textBox.clientWidth + options.x;
    }
    cxe = cxs + options.x;
  } else {
    cxs = bb.x + bb.width / 2;
    cxe = cxs + options.x;
    left = cxe - textBox.clientWidth / 2;
    if (options.y > 0) {
      // bottom
      cys = bb.y + bb.height;
      top = cys + options.y;
    } else {
      // top
      cys = bb.y;
      top = bb.y - textBox.clientHeight + options.y;
    }
    cye = cys + options.y;
  }

  textBox.style.top = top + 'px';
  textBox.style.left = left + 'px';

  const svgC = document.createElement('div');
  svgC.style.position = 'absolute';
  svgC.style.zIndex = '99999999';
  svgC.style.top = '0';
  svgC.classList.add('SCREENSHOT__ANNOTATION');

  const height = Math.max(top + textBox.clientHeight, bb.y + bb.height);
  const width = Math.max(left + textBox.clientWidth, bb.x + bb.width);

  svgC.innerHTML = `<svg viewBox="0 0 ${width} ${height}" xmlns="http://www.w3.org/2000/svg" style="width: ${width}px; height: ${height}px;">
  <defs>
    <!-- arrowhead marker definition -->
    <marker id="arrow" viewBox="0 0 10 10" refX="5" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
      <path d="M 0 0 L 10 5 L 0 10 z"></path>
    </marker>
  </defs>

  <polyline points="${cxe},${cye}, ${cxs},${cys}" fill="none" stroke="black" stroke-width="3" marker-end="url(#arrow)"></polyline>
</svg>`;

  document.body.appendChild(svgC);
});

export const clearAllAnnotations = ClientFunction(() => {
  const nodes = document.querySelectorAll('.SCREENSHOT__ANNOTATION');

  nodes.forEach((node) => {
    document.body.removeChild(node);
  });
});
