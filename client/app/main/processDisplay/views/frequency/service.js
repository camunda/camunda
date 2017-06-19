import generateHeatmap from './generateHeatmap';
import {addDiagramTooltip} from '../service';

export function addHeatmapOverlay(viewer, element, data, createTooltip) {
  const value = data[element];

  if (value !== undefined) {
    const tooltipContent = createTooltip(value);

    addDiagramTooltip(viewer, element, tooltipContent);
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
