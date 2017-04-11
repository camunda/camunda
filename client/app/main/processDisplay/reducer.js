import {reducer as diagramReducer} from './diagram';
import {reducer as statisticsReducer} from './statistics';
import {combineReducers} from 'redux';

export const reducer = combineReducers({
  diagram: diagramReducer,
  statistics: statisticsReducer
});
