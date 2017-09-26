import {expect} from 'chai';
import sinon from 'sinon';
import {getRouter, createUrlTestForRoute, createUrlConstructForRoute, __set__, __ResetDependency__} from 'router/service';

describe.only('Router service', () => {
  describe('getRouter', () => {
    let $window;
    let $document;
    let dispatchAction;
    let createRouteAction;
    let router;

    beforeEach(() => {
      $window = {
        history: {
          replaceState: sinon.spy(),
          pushState: sinon.spy()
        }
      };
      __set__('$window', $window);

      $window.history.pushState.reset();
      $window.history.replaceState.reset();

      $document = {
        location: {
          pathname: '/ala/56',
          search: '?b=1'
        }
      };
      __set__('$document', $document);

      dispatchAction = sinon.spy();
      __set__('dispatchAction', dispatchAction);

      createRouteAction = (name, params) => ({name, params});
      __set__('createRouteAction', createRouteAction);

      __set__('router', null); // reset router for this test file

      router = getRouter();

      const route = {
        name: 'ala',
        url: '/ala/:ala'
      };

      router.addRoutes(route);
    });

    afterEach(() => {
      __ResetDependency__('$window');
      __ResetDependency__('$document');
      __ResetDependency__('dispatchAction');
      __ResetDependency__('createRouteAction');
      __ResetDependency__('router');
    });

    it('should create only one instance of router', () => {
      const router2 = getRouter();

      expect(router2).to.equal(router);
    });

    describe('addHistoryListener and fireHistoryListeners', () => {
      let listener;

      beforeEach(() => {
        listener = sinon.spy();

        router.addHistoryListener(listener);
      });

      it('should not fire listener when url did not change', () => {
        router.fireHistoryListeners();

        expect(listener.called)
          .to.eql(false, 'expected listener to not have been called');
      });

      it('should fire listener when url did change', () => {
        router.onUrlChange();
        router.fireHistoryListeners();

        expect(listener.called)
          .to.eql(true, 'expected listener to have been called');
      });
    });

    describe('with default parameters', () => {
      let route;

      beforeEach(() => {
        route = {
          name: 'with-defaults',
          url: '/with-defaults/:type/:name',
          defaults: {
            name: 'gdansk'
          }
        };

        router.addRoutes(route);
      });

      it('should match with missing params', () => {
        $document.location.pathname = '/with-defaults/city';
        $document.location.search = '';
        router.onUrlChange();

        expect(dispatchAction.called).to.eql(true, 'action should be dispatched');
        expect(dispatchAction.calledWith({
          name: 'with-defaults',
          params: {
            type: 'city',
            name: 'gdansk'
          }
        })).to.eql(true, 'expected daufault parameter to be added');
      });

      it('should not match with too long url', () => {
        $document.location.pathname = '/with-defaults/city/b/d';
        $document.location.search = '';
        router.onUrlChange();

        expect(dispatchAction.called).to.eql(false);
      });

      it('should build url with defaults correctly', () => {
        expect(router.getUrl(route.name, {type: 'stadt'}))
          .to.eql('/with-defaults/stadt/gdansk');
      });
    });

    describe('addRoutes', () => {
      it('should allow routes to be added', () => {
        const route = {
          name: 'd1',
          url: '/bbb/:a',
          reducer: () => 'reducer'
        };
        const route2 = {
          name: 'd1-2',
          url: '/aaa/:a'
        };

        router.addRoutes(route, route2);

        expect(router.getRouteReducer(route.name)()).to.equal('reducer');
        expect(router.getUrl(route.name, {a: 23, b: 45})).to.eql('/bbb/23?b=45');
        expect(router.getUrl(route2.name, {a: 23, b: 45})).to.eql('/aaa/23?b=45');
      });

      it('should allow adding custom test to route', () => {
        const route = {
          name: 'd',
          url: '/d',
          test: () => ({b: 1})
        };

        router.addRoutes(route);

        $document.location.pathname = 'whatever';
        router.onUrlChange();

        expect(dispatchAction.called).to.eql(true, 'action should be dispatched');
        expect(dispatchAction.calledWith({
          name: 'd',
          params: {b: 1}
        })).to.eql(true, 'custom test should be used');
      });

      it('should allow custom url constructor', () => {
        const route = {
          name: 'd2',
          url: '/d',
          construct: () => '/some-url'
        };

        router.addRoutes(route);

        expect(router.getUrl('d2', {})).to.eql('/some-url');
      });
    });

    describe('goTo', () => {
      it('should dispatch route change action', () => {
        router.goTo('ala', {});

        expect(dispatchAction.calledWith({
          name: 'ala',
          params: {}
        })).to.eql(true, 'expected correct action dispatched on goTo');
      });

      it('should push new state', () => {
        router.goTo('ala', {
          ala: 'alina',
          b: 23
        });

        expect($window.history.pushState.calledOnce).to.eql(true, 'expected history.pushState to be called once');
        expect($window.history.pushState.calledWith(null, null, '/ala/alina?b=23')).to.eql(true, 'expected correct url to be pushed');
      });

      it('should replace state when replace flag is set', () => {
        router.goTo(
          'ala',
          {
            ala: 'alina',
            b: 23
          },
          true
        );

        expect($window.history.replaceState.calledOnce).to.eql(true, 'expected history.pushState to be called once');
        expect($window.history.replaceState.calledWith(null, null, '/ala/alina?b=23')).to.eql(true, 'expected correct url to be pushed');
      });
    });

    describe('getUrl', () => {
      it('should create url for route', () => {
        expect(router.getUrl('ala', {ala: 'd', b: 45})).to.eql('/ala/d?b=45');
      });
    });

    describe('getRouteReducer', () => {
      it('should return undefined when reducer is not given', () => {
        expect(router.getRouteReducer('ala')).to.eql(undefined);
      });

      it('should return reducer for route', () => {
        const route = {
          name: 'getReducer-route1',
          url: 'd',
          reducer: 'reducer'
        };

        router.addRoutes(route);

        expect(router.getRouteReducer(route.name)).to.equal(route.reducer);
      });
    });

    describe('onUrlChange', () => {
      it('should update route for current url', () => {
        $document.location.pathname = '/ala/67';
        $document.location.search = '?b=34';

        router.onUrlChange();

        expect(dispatchAction.calledWith({
          name: 'ala',
          params: {
            ala: '67',
            b: '34'
          }
        })).to.eql(true, 'expect action to be dispatched');
      });

      it('should be fired on state pop state event', () => {
        $document.location.pathname = '/ala/678';
        $document.location.search = '?b=341';

        $window.onpopstate();

        expect(dispatchAction.calledWith({
          name: 'ala',
          params: {
            ala: '678',
            b: '341'
          }
        })).to.eql(true, 'expect action to be dispatched');
      });
    });
  });

  describe('createUrlTestForRoute', () => {
    let test;

    beforeEach(() => {
      test = createUrlTestForRoute('/url/:param/d/:other', {
        other: 'default',
        name: 'name2'
      });
    });

    it('should return matched params and defaults', () => {
      expect(test('/url/value1/d/value2', '')).to.eql({
        param: 'value1',
        other: 'value2',
        name: 'name2'
      });
    });

    it('should return undefined on missmatch', () => {
      expect(test('/url/value1/f/value2', '')).to.eql(undefined);
      expect(test('/url/value1/', '')).to.eql(undefined);
    });

    it('should add query parameters', () => {
      expect(test('/url/value1/d/value2', '?q1=value3')).to.eql({
        param: 'value1',
        other: 'value2',
        name: 'name2',
        q1: 'value3'
      });
    });

    it('should add override default with query parameter', () => {
      expect(test('/url/value1/d/value2', '?name=value3')).to.eql({
        param: 'value1',
        other: 'value2',
        name: 'value3',
      });
    });

    it('should match without part of url if there is default given', () => {
      expect(test('/url/value1/d', '?name=value3')).to.eql({
        param: 'value1',
        other: 'default',
        name: 'value3',
      });
    });
  });
  describe('createUrlConstructForRoute', () => {
    let construct;

    beforeEach(() => {
      construct = createUrlConstructForRoute('/url/:first/some/:second', {
        second: 'def1'
      });
    });

    it('should construct url with given params', () => {
      expect(construct({
        first: 'value1',
        second: 'value2'
      })).to.eql('/url/value1/some/value2');
    });

    it('should construct url with given default parameter', () => {
      expect(construct({
        first: 'value1'
      })).to.eql('/url/value1/some/def1');
    });

    it('should use extra parameters as query', () => {
      expect(construct({
        first: 'value1',
        other: 'value2'
      })).to.eql('/url/value1/some/def1?other=value2');

      expect(construct({
        first: 'value1',
        other: 'value2',
        other2: 'value3'
      })).to.eql('/url/value1/some/def1?other=value2&other2=value3');
    });

    it('should use extra parameters as query with constant urls', () => {
      construct = createUrlConstructForRoute('/url', {});

      expect(construct({
        other: 'value2'
      })).to.eql('/url?other=value2');

      expect(construct({
        other: 'value2',
        other2: 'value3'
      })).to.eql('/url?other=value2&other2=value3');
    });
  });
});
