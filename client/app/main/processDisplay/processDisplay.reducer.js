import {reducer as filtersReducer} from './filters';
import {reducer as diagramReducer} from './diagram';
import {combineReducers} from 'redux';

export const reducer = combineReducers({
  filters: filtersReducer,
  diagram: diagramReducer
});
