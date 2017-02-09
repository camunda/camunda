import {reducer as diagramReducer} from './diagram';
import {reducer as controlsReducer} from './controls';
import {addLoading, createLoadingActionFunction, createResultActionFunction} from 'utils';
import {combineReducers} from 'redux';

export const reducer = combineReducers({
  display: addLoading(diagramReducer, 'diagram', 'heatmap'),
  controls: controlsReducer
});

export const createLoadingDiagramAction = createLoadingActionFunction('diagram');
export const createLoadingDiagramResultAction = createResultActionFunction('diagram');
export const createLoadingHeatmapAction = createLoadingActionFunction('heatmap');
export const createLoadingHeatmapResultAction = createResultActionFunction('heatmap');
