import {expect} from 'chai';
import sinon from 'sinon';
import {getRouter, __set__, __ResetDependency__} from 'router/router.service';

describe('Router service', () => {
  describe('getRouter', () => {
    let $window;
    let $document;
    let dispatchAction;
    let createRouteAction;
    let router;

    before(() => {
      $window = {
        history: {
          replaceState: sinon.spy(),
          pushState: sinon.spy()
        }
      };
      __set__('$window', $window);
    });

    after(() => {
      __ResetDependency__('$window');
    });

    beforeEach(() => {
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

      router = getRouter();

      const route = {
        name: 'ala',
        url: '/ala/:ala?b=:b'
      };

      router.addRoutes(route);
    });

    afterEach(() => {
      __ResetDependency__('$document');
      __ResetDependency__('dispatchAction');
      __ResetDependency__('createRouteAction');
    });

    it('should create only one instance of router', () => {
      const router2 = getRouter();

      expect(router2).to.equal(router);
    });

    describe('addRoutes', () => {
      it('should allow routes to be added', () => {
        const route = {
          name: 'd1',
          url: '/bbb/:a?b=:b',
          reducer: () => 'reducer'
        };
        const route2 = {
          name: 'd1-2',
          url: '/aaa/:a?b=:b'
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
});
