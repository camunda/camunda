import {expect} from 'chai';
import {createRouteAction, createRouterReducer, CHANGE_ROUTE_ACTION} from 'router/router.reducer';
import sinon from 'sinon';

describe('Router reducer', () => {
  const childState = 'child-state';
  const name = 'route-g';
  let routeReducer;
  let router;
  let reducer;
  let params;

  beforeEach(() => {
    routeReducer = sinon
      .stub()
      .returns(childState);

    router = {
      getRouteReducer: sinon
        .stub()
        .returns(routeReducer)
    };

    reducer = createRouterReducer(router);
    params = {g1: 123}
  });

  it('should set new route on state', () => {
    const action = createRouteAction(name, params);

    const {route} = reducer(undefined, action);

    expect(route).to.eql({
      name,
      params
    });
  });

  it('should set child state', () => {
    const action = createRouteAction(name, params);

    const {childState: actualChildState} = reducer(undefined, action);

    expect(actualChildState).to.eql(childState);
  });

  it('should ignore non route action', () => {
    const action = {
      type: 'd',
      route: {
        name,
        params
      }
    };

    const {route, childState} = reducer(undefined, action);

    expect(route).to.eql(null);
    expect(childState).to.eql(null);
  });

  it('should not change other state properties', () => {
    const action = createRouteAction(name, params);
    const originalState = {a: 1, route: 'b'};

    const {a, route} = reducer(originalState, action);

    expect(a).to.eql(1);
    expect(route).to.eql({
      name,
      params
    });
  });

  it('should not change original state', () => {
    const action = createRouteAction(name, params);
    const originalState = {};

    const state = reducer(originalState, action);

    expect(state).not.to.equal(originalState);
    expect(originalState).to.eql({});
  });
});

describe('createRouteAction', () => {
  it('should create action with route property using given name and params', () => {
    const name = 'd1';
    const params = {b: 1};
    const {type, route} = createRouteAction(name, params);

    expect(type).to.eql(CHANGE_ROUTE_ACTION);
    expect(route.name).to.eql(name);
    expect(route.params).to.eql(params);
  });
});
