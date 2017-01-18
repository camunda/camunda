import {dispatchAction} from 'view-utils';
import generateHeatmap from './heatmap';
import {getHeatmapData, getDiagramXml} from './diagramBackend.mock';
import {createLoadingDiagramAction, createLoadingDiagramResultAction, createLoadingHeatmapAction, createLoadingHeatmapResultAction} from './diagram.reducer';

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

export function loadHeatmap(diagram) {
  dispatchAction(createLoadingHeatmapAction());
  getHeatmapData(diagram.id).then(result => {
    dispatchAction(createLoadingHeatmapResultAction(result));
  });
}

export function loadDiagram(diagram) {
  dispatchAction(createLoadingDiagramAction());
  getDiagramXml(diagram.id).then(result => {
    dispatchAction(createLoadingDiagramResultAction(result));
  });
}
