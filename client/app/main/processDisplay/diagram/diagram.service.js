import generateHeatmap from './heatmap';
import {getHeatmapData} from './diagramBackend.mock';

export function getHeatmap(viewer, processInstanceId) {
  return getHeatmapData(processInstanceId).then(data => {
    const heat = generateHeatmap(viewer, data);
    const node = document.createElementNS('http://www.w3.org/2000/svg', 'image');

    Object.keys(heat.dimensions).forEach(prop => {
      node.setAttributeNS(null, prop, heat.dimensions[prop]);
    });

    node.setAttributeNS('http://www.w3.org/1999/xlink', 'xlink:href', heat.img);
    node.setAttributeNS(null, 'style', 'opacity: 0.8; pointer-events: none;');

    return node;
  });
}
