import {expect} from 'chai';
import {setupPromiseMocking} from 'testHelpers';
import sinon from 'sinon';
import {performLogin, __set__, __ResetDependency__} from 'main/loginForm/service';

describe('loginForm service', () => {
  const errorChangeAction = 'error-change';
  const loginInProgressAction = 'login-in-progress';
  let dispatchAction;
  let router;
  let getLastRoute;
  let login;
  let createLoginErrorAction;
  let createLoginInProgressAction;

  setupPromiseMocking();

  beforeEach(() => {
    dispatchAction = sinon.spy();
    __set__('dispatchAction', dispatchAction);

    router = {
      goTo: sinon.spy()
    };
    __set__('router', router);

    getLastRoute = sinon.stub();
    __set__('getLastRoute', getLastRoute);

    login = sinon.stub();
    __set__('login', login);

    createLoginErrorAction = sinon.stub().returns(errorChangeAction);
    __set__('createLoginErrorAction', createLoginErrorAction);

    createLoginInProgressAction = sinon.stub().returns(loginInProgressAction);
    __set__('createLoginInProgressAction', createLoginInProgressAction);
  });

  afterEach(() => {
    __ResetDependency__('dispatchAction');
    __ResetDependency__('router');
    __ResetDependency__('getLastRoute');
    __ResetDependency__('login');
    __ResetDependency__('createLoginErrorAction');
    __ResetDependency__('createLoginInProgressAction');
  });

  describe('performLogin', () => {
    const user = 'user';
    const password = 'password1';
    let lastRoute;

    beforeEach(() => {
      login.returns(
        Promise.resolve('ok')
      );

      lastRoute = {
        params: {
          name: 'name',
          params: JSON.stringify({a: 1})
        }
      };

      getLastRoute.returns(lastRoute);
    });

    it('should call login function with user and password', () => {
      performLogin(user, password);

      expect(login.calledWith(user, password))
        .to.eql(true, 'expected login function to be called with user and password');
    });

    it('should dispatch login in progress action', () => {
      performLogin(user, password);

      expect(createLoginInProgressAction.called)
        .to.eql(true, 'expected login in progress action to be created');
      expect(dispatchAction.calledWith(loginInProgressAction))
        .to.eql(true, 'expected login in progress action to be dispatched');
    });

    describe('on successful login', () => {
      beforeEach(() => {
        performLogin(user, password);
        Promise.runAll();
      });

      it('should call fetch last route params when login is successful', () => {
        expect(getLastRoute.calledOnce).to.eql(true, 'expected last route to be fetch');
      });

      it('should dispatch login error action with error flag set to false', () => {
        expect(dispatchAction.calledWith(errorChangeAction))
          .to.eql(true, 'expected error action to be dispatched');
        expect(createLoginErrorAction.calledWith(false))
          .to.eql(true, 'expected error action to be created with false error flag');
      });

      it('should go to previous route on successful login', () => {
        expect(router.goTo.calledWith(
          lastRoute.params.name,
          JSON.parse(lastRoute.params.params)
        )).to.eql(true, 'expected to be redirected to previous route');
      });
    });

    describe('on failed login', () => {
      const ERROR_MSG ='I_AM_ERROR';

      beforeEach(() => {
        login.returns(
          Promise.reject(ERROR_MSG)
        );

        performLogin(user, password);
        Promise.runAll();
      });

      afterEach(() => {
        __ResetDependency__('addNotification');
      });

      it('should dispatch error action with error flag set to true', () => {
        expect(dispatchAction.calledWith(errorChangeAction))
          .to.eql(true, 'expected error action to be dispatched');
        expect(createLoginErrorAction.calledWith(true))
          .to.eql(true, 'expected error action to be created with true error flag');
      });
    });
  });
});
