/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  append as svgAppend,
  attr as svgAttr,
  create as svgCreate,
} from 'tiny-svg';

import {is} from 'bpmn-js/lib/util/ModelUtil';
import {EventCallback} from 'bpmn-js/lib/NavigatedViewer';

const PADDING = 4;

function Outline(eventBus: {on: EventCallback}) {
  eventBus.on('shape.added', (event) => {
    const element = event.element;
    const gfx = event.gfx;

    const outlineAttributes = {
      fill: 'none',
      class: 'djs-outline',
    };

    if (is(element, 'bpmn:Activity')) {
      const outline = svgCreate('rect');

      svgAttr(outline, {
        ...outlineAttributes,
        x: -PADDING,
        y: -PADDING,
        rx: '12px',
        ry: '12px',

        width: element.width + PADDING * 2,
        height: element.height + PADDING * 2,
      });

      svgAppend(gfx, outline);
    } else if (is(element, 'bpmn:Event')) {
      const outline = svgCreate('circle');

      svgAttr(outline, {
        ...outlineAttributes,
        r: element.width / 2 + PADDING,
        cx: element.width / 2,
        cy: element.width / 2,
      });

      svgAppend(gfx, outline);
    } else if (is(element, 'bpmn:Gateway')) {
      const outline = svgCreate('polygon');
      const GATEWAY_PADDING = PADDING * Math.sqrt(2);
      const top = `${element.width / 2},${-GATEWAY_PADDING}`;
      const right = `${element.width + GATEWAY_PADDING},${element.height / 2}`;
      const bottom = `${element.width / 2},${element.height + GATEWAY_PADDING}`;
      const left = `${-GATEWAY_PADDING},${element.height / 2}`;

      svgAttr(outline, {
        ...outlineAttributes,
        points: `${top} ${right} ${bottom} ${left}`,
      });

      svgAppend(gfx, outline);
    }
  });
}

Outline.$inject = ['eventBus'];

const OutlineModule = {outline: ['type', Outline]};
export {OutlineModule};
