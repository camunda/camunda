export const filterType = 'executedNode';
const ADD_FLOW_NODES_FILTER = 'ADD_FLOW_NODES_FILTER';

export function routeReducer(filters = [], action) {
  if (action.type === ADD_FLOW_NODES_FILTER) {
    const {data} = action;
    const newFilter = {
      type: filterType,
      data
    };

    return filters.concat(newFilter);
  }

  return filters;
}

export function createAddFlowNodesFilterAction(selected) {
  return {
    type: ADD_FLOW_NODES_FILTER,
    data: selected
  };
}
