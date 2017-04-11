import {expect} from 'chai';
import sinon from 'sinon';
import {addRouteReducer, __set__, __ResetDependency__} from 'router/addRouteReducer';

describe('addRouteReducer', () => {
  const route = 'some-route';
  let getLastRoute;
  let router;
  let params;
  let dispatch;

  beforeEach(() => {
    params = {
      a: 5
    };

    getLastRoute = sinon.stub().returns({
      name: route,
      params
    });
    __set__('getLastRoute', getLastRoute);

    router = {
      goTo: sinon.spy()
    };
    __set__('router', router);

    dispatch = addRouteReducer({parse, format, reducer});
  });

  afterEach(() => {
    __ResetDependency__('getLastRoute');
    __ResetDependency__('router');
  });

  it('should return dispatch function', () => {
    expect(typeof dispatch).to.eql('function');
  });

  describe('dispatch', () => {
    it('should use reducer to transform url params', () => {
      dispatch({diff: 1});

      expect(router.goTo.calledWith(route, {
        a: 6
      })).to.eql(true);
    });

    it('should return new state', () => {
      const state = dispatch({diff: 1});

      expect(state).to.eql({b: 6});
    });
  });

  function reducer(state = {b: 1}, action) {
    return {
      ...state,
      b: state.b + action.diff
    };
  }

  function parse({a}) {
    return {b: a};
  }

  function format(params, {b}) {
    return {
      a: b
    };
  }
});
