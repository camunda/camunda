import {reducer as analyticsReducer} from './analytics';
import {reducer as targetValueReducer} from './targetValueDisplay';
import {addLoading, createLoadingActionFunction, createResultActionFunction, createErrorActionFunction} from 'utils';

export const xmlProperty = 'bpmnXml';
export const heatmapProperty = 'heatmap';
export const targetValueProperty = 'targetValue';

export const reducer = addLoading((state = {}, action) => {
  return {
    ...state,
    analytics: analyticsReducer(state.analytics, action),
    targetValue: targetValueReducer(state.targetValue, action)
  };
}, xmlProperty, heatmapProperty, targetValueProperty);

export const createLoadingDiagramAction = createLoadingActionFunction(xmlProperty);
export const createLoadingDiagramResultAction = createResultActionFunction(xmlProperty);
export const createLoadingDiagramErrorAction = createErrorActionFunction(xmlProperty);
export const createLoadingHeatmapAction = createLoadingActionFunction(heatmapProperty);
export const createLoadingHeatmapResultAction = createResultActionFunction(heatmapProperty);
export const createLoadingHeatmapErrorAction = createErrorActionFunction(heatmapProperty);
export const createLoadingTargetValueAction = createLoadingActionFunction(targetValueProperty);
export const createLoadingTargetValueResultAction = createResultActionFunction(targetValueProperty);
export const createLoadingTargetValueErrorAction = createErrorActionFunction(targetValueProperty);
