import {combineReducers} from 'redux';
import {reducer as filter} from './filter';
import {reducer as view} from './view';

export const reducer = combineReducers({
  filter,
  view
});
