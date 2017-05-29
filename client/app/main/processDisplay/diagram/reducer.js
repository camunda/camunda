import {reducer as analyticsReducer} from './analytics';
import {reducer as targetValueReducer} from './targetValueDisplay';
import {addLoading, createLoadingActionFunction, createResultActionFunction, createErrorActionFunction} from 'utils';

export const reducer = addLoading((state = {}, action) => {
  return {
    ...state,
    analytics: analyticsReducer(state.analytics, action),
    targetValue: targetValueReducer(state.targetValue, action)
  };
}, 'bpmnXml', 'heatmap', 'targetValue');

export const createLoadingDiagramAction = createLoadingActionFunction('bpmnXml');
export const createLoadingDiagramResultAction = createResultActionFunction('bpmnXml');
export const createLoadingDiagramErrorAction = createErrorActionFunction('bpmnXml');
export const createLoadingHeatmapAction = createLoadingActionFunction('heatmap');
export const createLoadingHeatmapResultAction = createResultActionFunction('heatmap');
export const createLoadingHeatmapErrorAction = createErrorActionFunction('heatmap');
export const createLoadingTargetValueAction = createLoadingActionFunction('targetValue');
export const createLoadingTargetValueResultAction = createResultActionFunction('targetValue');
export const createLoadingTargetValueErrorAction = createErrorActionFunction('targetValue');
