import {reducer as diagramReducer} from './diagram';
import {reducer as processDefinitionReducer} from './controls/processDefinition/processDefinition.reducer';
import {combineReducers} from 'redux';

export const reducer = combineReducers({
  display: diagramReducer,
  processDefinition: processDefinitionReducer
});
