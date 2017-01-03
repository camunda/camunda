import {reducer as filtersReducer} from './filters';
import {combineReducers} from 'redux';

export const reducer = combineReducers({
  filters: filtersReducer
});
