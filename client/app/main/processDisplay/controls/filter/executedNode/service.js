import {dispatch} from '../store';
import {createChangeSelectNodesAction} from './routeReducer';

export function changeSelectedNodes(selected) {
  dispatch(createChangeSelectNodesAction(selected));
}
