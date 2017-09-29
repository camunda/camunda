export {
  reducer, createLoadingDiagramAction, createLoadingDiagramResultAction,
  createLoadingDiagramErrorAction, createLoadingHeatmapAction,
  createLoadingHeatmapResultAction, createLoadingHeatmapErrorAction,
  createLoadingTargetValueAction, createLoadingTargetValueResultAction,
  createLoadingTargetValueErrorAction,
  xmlProperty, heatmapProperty, targetValueProperty
} from './reducer';
export {resetStatisticData} from './analytics';
export {ViewsDiagramArea} from './ViewsDiagramArea';
export {ViewsArea} from './ViewsArea';
export {definitions} from './viewDefinitions';
export {getDiagramXML} from './selectors';
