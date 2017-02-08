import {reducer as diagramReducer} from './diagram';
import {reducer as processDefinitionReducer, SELECT_PROCESS_DEFINITION} from './controls/processDefinition/reducer';
import {reducer as createFilterReducer} from './controls/filterCreation/reducer';
import {addLoading, createLoadingActionFunction, createResultActionFunction, INITIAL_STATE} from 'utils/loading';
import {combineReducers} from 'redux';

export const reducer = combineReducers({
  display: handleFilterChange(addLoading(diagramReducer, 'diagram', 'heatmap')),
  processDefinition: processDefinitionReducer,
  createFilter: createFilterReducer
});

function handleFilterChange(next) {
  return (state = {}, action) => {
    if (action.type === SELECT_PROCESS_DEFINITION) {
      return {
        ...state,
        diagram: {state: INITIAL_STATE},
        heatmap: {state: INITIAL_STATE}
      };
    }

    return next(state, action);
  };
}

export const createLoadingDiagramAction = createLoadingActionFunction('diagram');
export const createLoadingDiagramResultAction = createResultActionFunction('diagram');
export const createLoadingHeatmapAction = createLoadingActionFunction('heatmap');
export const createLoadingHeatmapResultAction = createResultActionFunction('heatmap');
