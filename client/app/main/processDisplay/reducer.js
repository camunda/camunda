import {reducer as views} from './views';
import {reducer as statistics} from './statistics';
import {reducer as controls} from './controls';
import {combineReducers} from 'redux';

export const reducer = combineReducers({
  views,
  statistics,
  controls
});
