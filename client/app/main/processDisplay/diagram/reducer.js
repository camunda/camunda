import {reducer as analyticsReducer} from './analytics';
import {addLoading, createLoadingActionFunction, createResultActionFunction, createErrorActionFunction} from 'utils';

export const reducer = addLoading(analyticsReducer, 'bpmnXml', 'heatmap');

export const createLoadingDiagramAction = createLoadingActionFunction('bpmnXml');
export const createLoadingDiagramResultAction = createResultActionFunction('bpmnXml');
export const createLoadingDiagramErrorAction = createErrorActionFunction('bpmnXml');
export const createLoadingHeatmapAction = createLoadingActionFunction('heatmap');
export const createLoadingHeatmapResultAction = createResultActionFunction('heatmap');
export const createLoadingHeatmapErrorAction = createErrorActionFunction('heatmap');
