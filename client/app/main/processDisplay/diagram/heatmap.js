import HeatmapJS from 'heatmap.js';

const SEQUENCEFLOW_RADIUS = 30;
const SEQUENCEFLOW_STEPWIDTH = 10;
const SEQUENCEFLOW_VALUE_MODIFIER = 0.2;
const ACTIVITY_DENSITY = 20;
const ACTIVITY_RADIUS = 60;
const ACTIVITY_VALUE_MODIFIER = 0.125;
const COOLNESS = 0.4;
const EDGE_BUFFER = 75;
const RESOLUTION = 4;

export default function generateHeatmap(viewer, data) {
  const dimensions = getDimensions(viewer);
  const heatmapData = generateData(data, viewer, dimensions);

  const max = Math.max.apply(null, Object.values(data));
  const map = createMap(dimensions);

  map.setData({
    min: 0,
    max: max * COOLNESS,
    data: heatmapData
  });

  return {
    img: map.getDataURL(),
    dimensions
  };
}

function getDimensions(viewer) {
  const dimensions = viewer
    .get('canvas')
    .getDefaultLayer()
    .getBBox();

  return {
    width: (dimensions.width + 2 * EDGE_BUFFER),
    height: (dimensions.height + 2 * EDGE_BUFFER),
    x: dimensions.x - EDGE_BUFFER,
    y: dimensions.y - EDGE_BUFFER
  };
}

function createMap(dimensions) {
  const container = document.createElement('div');

  container.style.width = (dimensions.width / RESOLUTION) + 'px';
  container.style.height = (dimensions.height / RESOLUTION) + 'px';
  container.style.position = 'absolute';

  document.body.appendChild(container);

  const map = HeatmapJS.create({container});

  document.body.removeChild(container);

  container.firstChild.setAttribute('width', dimensions.width / RESOLUTION);
  container.firstChild.setAttribute('height', dimensions.height / RESOLUTION);

  return map;
}

function generateData(values, viewer, {x: xOffset, y: yOffset}) {
  const data = [];
  const elementRegistry = viewer.get('elementRegistry');

  for (const key in values) {
    const element = elementRegistry.get(key);

    if (!element) {
      // for example for multi instance bodies
      continue;
    }

    // add multiple points evenly distributed in the area of the node. Number of points depends on area of node
    for (let i = 0; i < element.width+ACTIVITY_DENSITY/2; i+= ACTIVITY_DENSITY) {
      for (let j = 0; j < element.height+ACTIVITY_DENSITY/2; j+= ACTIVITY_DENSITY) {
        if (values[key] === 0) {
          values[key] += Number.EPSILON;
        }
        data.push({
          x: (element.x + i - xOffset) / RESOLUTION,
          y: (element.y + j - yOffset) / RESOLUTION,
          value: values[key] * ACTIVITY_VALUE_MODIFIER,
          radius: ACTIVITY_RADIUS / RESOLUTION
        });
      }
    }

    for (let i = 0; i < element.incoming.length; i++) {
      drawSequenceFlow(data, element.incoming[i].waypoints, Math.min(values[key], values[element.incoming[i].source.id]), {xOffset, yOffset});
    }
  }

  return data;
}

function drawSequenceFlow(data, waypoints, value, {xOffset, yOffset}) {
  if (!value) {
    return;
  }

  for (let i = 1; i < waypoints.length; i++) {
    const start = waypoints[i-1];
    const end = waypoints[i];

    const movementVector = {
      x: end.x - start.x,
      y: end.y - start.y
    };
    const normalizedMovementVector = {
      x: movementVector.x / (Math.abs(movementVector.x) + Math.abs(movementVector.y)) * SEQUENCEFLOW_STEPWIDTH,
      y: movementVector.y / (Math.abs(movementVector.x) + Math.abs(movementVector.y)) * SEQUENCEFLOW_STEPWIDTH
    };

    const numberSteps = Math.sqrt(movementVector.x * movementVector.x + movementVector.y * movementVector.y) / SEQUENCEFLOW_STEPWIDTH;

    for (let j = 0; j < numberSteps; j++) {
      data.push({
        x: (start.x + normalizedMovementVector.x * j - xOffset) / RESOLUTION,
        y: (start.y + normalizedMovementVector.y * j - yOffset) / RESOLUTION,
        value: value * SEQUENCEFLOW_VALUE_MODIFIER,
        radius: SEQUENCEFLOW_RADIUS / RESOLUTION
      });
    }
  }
}
