/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import HeatmapJS from 'heatmap.js';
import {createRoot} from 'react-dom/client';

const SEQUENCEFLOW_RADIUS = 30;
const SEQUENCEFLOW_STEPWIDTH = 10;
const SEQUENCEFLOW_VALUE_MODIFIER = 0.2;
const ACTIVITY_DENSITY = 20;
const ACTIVITY_RADIUS = 50;
const ACTIVITY_VALUE_MODIFIER = 0.125;
const VALUE_SHIFT = 0.17;
const COOLNESS = 2.5;
const EDGE_BUFFER = 75;
const RESOLUTION = 4;

export function getHeatmap(viewer, data, noSequenceHighlight) {
  const heat = generateHeatmap(viewer, data, noSequenceHighlight);
  const node = document.createElementNS('http://www.w3.org/2000/svg', 'image');

  Object.keys(heat.dimensions).forEach((prop) => {
    node.setAttributeNS(null, prop, heat.dimensions[prop]);
  });

  node.setAttributeNS('http://www.w3.org/1999/xlink', 'xlink:href', heat.img);
  node.setAttributeNS(null, 'style', 'opacity: 0.8; pointer-events: none;');

  return node;
}

function generateHeatmap(viewer, data, noSequenceHighlight) {
  const dimensions = getDimensions(viewer);
  let heatmapData = generateData(data, viewer, dimensions, noSequenceHighlight);

  const map = createMap(dimensions);
  const heatmapDataValueMax = Math.max.apply(
    this,
    heatmapData.map((el) => el.value)
  );

  heatmapData = heatmapData.map(({x, y, value, radius}) => {
    const shiftValue = noSequenceHighlight ? 0 : VALUE_SHIFT;
    return {
      x: Math.round(x),
      y: Math.round(y),
      radius,
      value: (shiftValue + (value / heatmapDataValueMax) * (1 - shiftValue)) / COOLNESS,
    };
  });

  map.setData({
    min: 0,
    max: 1,
    data: heatmapData,
  });

  return {
    img: map.getDataURL(),
    dimensions,
  };
}

function getDimensions(viewer) {
  const dimensions = viewer.get('canvas').getActiveLayer().getBBox();

  return {
    width: dimensions.width + 2 * EDGE_BUFFER,
    height: dimensions.height + 2 * EDGE_BUFFER,
    x: dimensions.x - EDGE_BUFFER,
    y: dimensions.y - EDGE_BUFFER,
  };
}

function createMap(dimensions) {
  const container = document.createElement('div');

  container.style.width = dimensions.width / RESOLUTION + 'px';
  container.style.height = dimensions.height / RESOLUTION + 'px';
  container.style.position = 'absolute';

  document.body.appendChild(container);

  const map = HeatmapJS.create({container});

  document.body.removeChild(container);

  container.firstChild.setAttribute('width', dimensions.width / RESOLUTION);
  container.firstChild.setAttribute('height', dimensions.height / RESOLUTION);

  return map;
}

function isBpmnType(element, types) {
  if (typeof types === 'string') {
    types = [types];
  }
  return (
    element.type !== 'label' &&
    types.filter((type) => element.businessObject.$instanceOf('bpmn:' + type)).length > 0
  );
}

function isExcluded(element) {
  return isBpmnType(element, 'SubProcess') && !element.collapsed;
}

function generateData(values, viewer, {x: xOffset, y: yOffset}, noSequenceHighlight) {
  const data = [];
  const elementRegistry = viewer.get('elementRegistry');

  for (const key in values) {
    const element = elementRegistry.get(key);

    if (!element || typeof values[key] !== 'number') {
      // for example for multi instance bodies
      continue;
    }

    if (!isExcluded(element)) {
      // add multiple points evenly distributed in the area of the node. Number of points depends on area of node
      for (let i = 0; i < element.width + ACTIVITY_DENSITY / 2; i += ACTIVITY_DENSITY) {
        for (let j = 0; j < element.height + ACTIVITY_DENSITY / 2; j += ACTIVITY_DENSITY) {
          const value = values[key] === 0 ? Number.EPSILON : values[key];

          data.push({
            x: (element.x + i - xOffset) / RESOLUTION,
            y: (element.y + j - yOffset) / RESOLUTION,
            value: value * ACTIVITY_VALUE_MODIFIER,
            radius: ACTIVITY_RADIUS / RESOLUTION,
          });
        }
      }
    }

    if (!noSequenceHighlight) {
      for (let i = 0; i < element.incoming.length; i++) {
        drawSequenceFlow(
          data,
          element.incoming[i].waypoints,
          Math.min(values[key], values[element.incoming[i].source.id]),
          {xOffset, yOffset}
        );
      }
    }
  }

  return data;
}

function drawSequenceFlow(data, waypoints, value, {xOffset, yOffset}) {
  if (!value) {
    return;
  }

  for (let i = 1; i < waypoints.length; i++) {
    const start = waypoints[i - 1];
    const end = waypoints[i];

    const movementVector = {
      x: end.x - start.x,
      y: end.y - start.y,
    };
    const normalizedMovementVector = {
      x:
        (movementVector.x / (Math.abs(movementVector.x) + Math.abs(movementVector.y))) *
        SEQUENCEFLOW_STEPWIDTH,
      y:
        (movementVector.y / (Math.abs(movementVector.x) + Math.abs(movementVector.y))) *
        SEQUENCEFLOW_STEPWIDTH,
    };

    const numberSteps =
      Math.sqrt(movementVector.x * movementVector.x + movementVector.y * movementVector.y) /
      SEQUENCEFLOW_STEPWIDTH;

    for (let j = 0; j < numberSteps; j++) {
      data.push({
        x: (start.x + normalizedMovementVector.x * j - xOffset) / RESOLUTION,
        y: (start.y + normalizedMovementVector.y * j - yOffset) / RESOLUTION,
        value: value * SEQUENCEFLOW_VALUE_MODIFIER,
        radius: SEQUENCEFLOW_RADIUS / RESOLUTION,
      });
    }
  }
}

export async function addDiagramTooltip({
  viewer,
  element,
  tooltipContent,
  theme,
  onMouseEnter,
  onMouseLeave,
}) {
  const elementGraphics = viewer.get('elementRegistry').getGraphics(element);
  const overlaysContainer = document.body.querySelector('.djs-overlay-container');
  if (!elementGraphics || !tooltipContent || !overlaysContainer) {
    return;
  }

  // create overlay node from html
  const overlayHtml = document.createElement('div');
  overlayHtml.classList.add('Tooltip', theme === 'light' ? 'light' : 'dark');

  if (onMouseEnter) {
    overlayHtml.addEventListener('mouseenter', onMouseEnter);
  }

  if (onMouseLeave) {
    overlayHtml.addEventListener('mouseleave', onMouseLeave);
  }

  // render tooltip react markup into the html tooltip container
  await insertReactContent(<div className="tooltipBridge">{tooltipContent}</div>, overlayHtml);
  overlaysContainer.appendChild(overlayHtml);
  const overlayHeight = overlayHtml.clientHeight;
  overlaysContainer.removeChild(overlayHtml);

  const nodeWidth = elementGraphics.querySelector('.djs-visual').getBBox().width;
  const position = {
    left: nodeWidth / 2,
  };

  if (
    viewer.get('elementRegistry').get(element).y - viewer.get('canvas').viewbox().y <
    overlayHeight
  ) {
    position.bottom = 0;
    const classList = overlayHtml.classList;
    classList.remove('top');
    classList.add('bottom');
  } else {
    position.top = 0;
  }

  // add overlay to viewer
  return viewer.get('overlays').add(element, 'TOOLTIP', {
    position,
    show: {
      minZoom: -Infinity,
      maxZoom: +Infinity,
    },
    html: overlayHtml,
  });
}

function insertReactContent(tooltipContent, overlayHtml) {
  return new Promise((resolve) => {
    createRoot(overlayHtml).render(tooltipContent);
    requestIdleCallback(resolve);
  });
}
