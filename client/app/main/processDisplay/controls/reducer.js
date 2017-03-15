import {combineReducers} from 'redux';
import {reducer as processDefinition} from './processDefinition';
import {reducer as filter} from './filter';
import {reducer as view} from './view';

export const reducer = combineReducers({
  processDefinition,
  filter,
  view
});
