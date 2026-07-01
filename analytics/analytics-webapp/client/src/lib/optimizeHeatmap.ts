/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/* eslint-disable @typescript-eslint/no-explicit-any */

// A near-verbatim port of Optimize's `HeatmapOverlay/service.js` (the `getHeatmap` path) so the
// rendering is identical: it distributes weighted points across each node's area and along its
// incoming sequence flows, renders them with heatmap.js, and returns an SVG <image> to overlay on
// the bpmn-js canvas. Uses the vendored, strict-safe heatmap.js. The React tooltip helpers from the
// original are intentionally omitted (the component provides its own hover tooltip).
import HeatmapJS from "../vendor/heatmapjs";

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

export function getHeatmap(
  viewer: any,
  data: Record<string, number>,
  noSequenceHighlight = false,
): SVGImageElement {
  const heat = generateHeatmap(viewer, data, noSequenceHighlight);
  const node = document.createElementNS("http://www.w3.org/2000/svg", "image");

  Object.keys(heat.dimensions).forEach((prop) => {
    node.setAttributeNS(null, prop, String((heat.dimensions as any)[prop]));
  });

  node.setAttributeNS("http://www.w3.org/1999/xlink", "xlink:href", heat.img);
  node.setAttributeNS(null, "style", "opacity: 0.8; pointer-events: none;");

  return node;
}

function generateHeatmap(viewer: any, data: Record<string, number>, noSequenceHighlight: boolean) {
  const dimensions = getDimensions(viewer);
  let heatmapData = generateData(data, viewer, dimensions, noSequenceHighlight);

  const map = createMap(dimensions);
  const heatmapDataValueMax = Math.max.apply(
    null,
    heatmapData.map((el) => el.value),
  );

  heatmapData = heatmapData.map(({ x, y, value, radius }) => {
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

function getDimensions(viewer: any) {
  const dimensions = viewer.get("canvas").getActiveLayer().getBBox();

  return {
    width: dimensions.width + 2 * EDGE_BUFFER,
    height: dimensions.height + 2 * EDGE_BUFFER,
    x: dimensions.x - EDGE_BUFFER,
    y: dimensions.y - EDGE_BUFFER,
  };
}

function createMap(dimensions: { width: number; height: number }) {
  const container = document.createElement("div");

  container.style.width = dimensions.width / RESOLUTION + "px";
  container.style.height = dimensions.height / RESOLUTION + "px";
  container.style.position = "absolute";

  document.body.appendChild(container);

  const map = HeatmapJS.create({ container });

  document.body.removeChild(container);

  (container.firstChild as HTMLElement).setAttribute("width", String(dimensions.width / RESOLUTION));
  (container.firstChild as HTMLElement).setAttribute(
    "height",
    String(dimensions.height / RESOLUTION),
  );

  return map;
}

function isBpmnType(element: any, types: string | string[]) {
  if (typeof types === "string") {
    types = [types];
  }
  return (
    element.type !== "label" &&
    types.filter((type) => element.businessObject.$instanceOf("bpmn:" + type)).length > 0
  );
}

function isExcluded(element: any) {
  return isBpmnType(element, "SubProcess") && !element.collapsed;
}

interface HeatPoint {
  x: number;
  y: number;
  value: number;
  radius: number;
}

function generateData(
  values: Record<string, number>,
  viewer: any,
  { x: xOffset, y: yOffset }: { x: number; y: number },
  noSequenceHighlight: boolean,
): HeatPoint[] {
  const data: HeatPoint[] = [];
  const elementRegistry = viewer.get("elementRegistry");

  for (const key in values) {
    const element = elementRegistry.get(key);

    if (!element || typeof values[key] !== "number") {
      // for example for multi instance bodies
      continue;
    }

    if (!isExcluded(element)) {
      // add multiple points evenly distributed in the area of the node
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
          { xOffset, yOffset },
        );
      }
    }
  }

  return data;
}

function drawSequenceFlow(
  data: HeatPoint[],
  waypoints: { x: number; y: number }[],
  value: number,
  { xOffset, yOffset }: { xOffset: number; yOffset: number },
) {
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
