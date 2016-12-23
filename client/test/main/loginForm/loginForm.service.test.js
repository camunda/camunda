import {expect} from 'chai';
import {setupPromiseMocking} from 'testHelpers';
import sinon from 'sinon';
import {performLogin, changeUser, changePassword, __set__, __ResetDependency__} from 'main/loginForm/loginForm.service';

describe('loginForm service', () => {
  const passChangeAction = 'password-change';
  const userChangeAction = 'user-change';
  const errorChangeAction = 'error-change';
  let dispatchAction;
  let router;
  let getLastRoute;
  let login;
  let createChangeLoginPasswordAction;
  let createChangeLoginUserAction;
  let createLoginErrorAction;

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

    createChangeLoginPasswordAction = sinon.stub().returns(passChangeAction);
    __set__('createChangeLoginPasswordAction', createChangeLoginPasswordAction);

    createChangeLoginUserAction = sinon.stub().returns(userChangeAction);
    __set__('createChangeLoginUserAction', createChangeLoginUserAction);

    createLoginErrorAction = sinon.stub().returns(errorChangeAction);
    __set__('createLoginErrorAction', createLoginErrorAction);
  });

  afterEach(() => {
    __ResetDependency__('dispatchAction');
    __ResetDependency__('router');
    __ResetDependency__('getLastRoute');
    __ResetDependency__('login');
    __ResetDependency__('createChangeLoginPasswordAction');
    __ResetDependency__('createChangeLoginUserAction');
    __ResetDependency__('createLoginErrorAction');
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
        )).to.eql(true, 'expected to be redirected to previous route')
      });
    });

    describe('on failed login', () => {
      it('should dispatch error action with error flag set to true', () => {
        login.returns(
          Promise.reject('err')
        );

        performLogin(user, password);
        Promise.runAll();

        expect(dispatchAction.calledWith(errorChangeAction))
          .to.eql(true, 'expected error action to be dispatched');
        expect(createLoginErrorAction.calledWith(true))
          .to.eql(true, 'expected error action to be created with true error flag');
      });
    });
  });

  describe('changeUser', () => {
    it('expected action to be dispatched on change', () => {
      const user = 'd1';

      changeUser(user);

      expect(dispatchAction.calledWith(userChangeAction)).to.eql(true);
      expect(createChangeLoginUserAction.calledWith(user)).to.eql(true);
    });
  });

  describe('changePassword', () => {
    it('expected action to be dispatched on change', () => {
      const password = 'p1';

      changePassword(password);

      expect(dispatchAction.calledWith(passChangeAction)).to.eql(true);
      expect(createChangeLoginPasswordAction.calledWith(password)).to.eql(true);
    });
  });
});
