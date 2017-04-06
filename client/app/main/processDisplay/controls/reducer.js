import {combineReducers} from 'redux';
import {reducer as filter} from './filter';

export const reducer = combineReducers({
  filter
});
