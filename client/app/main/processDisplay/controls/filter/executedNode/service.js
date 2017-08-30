import {dispatch} from '../store';
import {createAddFlowNodesFilterAction} from './routeReducer';

export function addFlowNodesFilter(selected) {
  dispatch(createAddFlowNodesFilterAction(selected));
}
