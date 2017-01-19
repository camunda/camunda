import {reducer as diagramReducer} from './diagram';
import {combineReducers} from 'redux';

export const reducer = combineReducers({
  diagram: diagramReducer
});
