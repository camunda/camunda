export const CHANGE_ROUTE_ACTION = 'CHANGE_ROUTE_ACTION';

export function createRouteAction(name, params) {
  return {
    type: CHANGE_ROUTE_ACTION,
    route: {
      name,
      params
    }
  };
}

export function createRouterReducer(router) {
  return (state = {route: null, childState: null}, action) => {
    let resultState = {...state};

    if (action.type === CHANGE_ROUTE_ACTION) {
      if (shouldClearChildState(state, action)) {
        resultState.childState = null;
      }

      resultState.route = action.route;
    }

    if (resultState.route) {
      const routeReducer = router.getRouteReducer(resultState.route.name);

      if (routeReducer) {
        resultState.childState = routeReducer(resultState.childState, action);
      }
    }

    return resultState;
  }
}

function shouldClearChildState({route: stateRoute}, {route: actionRoute}) {
  return !stateRoute && actionRoute || !actionRoute ||
    (stateRoute && actionRoute && stateRoute.name !== actionRoute.name);
}
