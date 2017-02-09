import {combineReducers} from 'redux';
import {reducer as processDefinition} from './processDefinition';
import {reducer as filter} from './filter';

export const reducer = combineReducers({
  processDefinition,
  filter
});
