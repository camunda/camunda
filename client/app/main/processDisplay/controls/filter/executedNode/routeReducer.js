export const filterType = 'executedNode';
const CHANGE_SELECTED_NODES = 'CHANGE_SELECTED_NODES';

export function routeReducer(filters = [], action) {
  if (action.type === CHANGE_SELECTED_NODES) {
    const {data} = action;
    const hasExecutedNodeFilter = filters.some(({type}) => type === filterType);
    const newFilter = {
      type: filterType,
      data
    };

    if (!hasExecutedNodeFilter) {
      return filters.concat(newFilter);
    }

    return filters.map(filter => {
      if (filter.type === filterType) {
        return newFilter;
      }

      return filter;
    });
  }

  return filters;
}

export function createChangeSelectNodesAction(selected) {
  return {
    type: CHANGE_SELECTED_NODES,
    data: selected
  };
}
