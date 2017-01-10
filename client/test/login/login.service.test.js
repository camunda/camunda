import {expect} from 'chai';
import sinon from 'sinon';
import {setupPromiseMocking} from 'testHelpers';
import {clearLogin, refreshAuthentication, login, __set__, __ResetDependency__} from 'login/login.service';

describe('Login service', () => {
  const LOGIN_KEY = 'KEY-1';
  let dispatchAction;
  let sessionStorage;
  let createClearLoginAction;
  let createLoginAction;
  let authenticate;
  let checkToken;

  setupPromiseMocking();

  beforeEach(() => {
    __set__('LOGIN_KEY', LOGIN_KEY);

    dispatchAction = sinon.spy();
    __set__('dispatchAction', dispatchAction);

    sessionStorage = {
      removeItem: sinon.spy(),
      setItem: sinon.spy(),
      getItem: sinon.stub()
    };
    __set__('sessionStorage', sessionStorage);

    createClearLoginAction = sinon.stub().returns('clear-login');
    __set__('createClearLoginAction', createClearLoginAction);

    createLoginAction = sinon.stub().returns('login-action');
    __set__('createLoginAction', createLoginAction);

    authenticate = sinon.stub();
    __set__('authenticate', authenticate);

    checkToken = sinon.stub();
    __set__('checkToken', checkToken);
  });

  afterEach(() => {
    __ResetDependency__('LOGIN_KEY');
    __ResetDependency__('dispatchAction');
    __ResetDependency__('sessionStorage');
  });

  describe('clearLogin', () => {
    beforeEach(() => {
      clearLogin();
    });

    it('should remove login from session storage', () => {
      expect(sessionStorage.removeItem.calledWith(LOGIN_KEY)).to.eql(true);
    });

    it('should dispatch clear login action', () => {
      expect(createClearLoginAction.calledOnce)
        .to.eql(true, 'expected clear login action to be created');
      expect(dispatchAction.calledWith('clear-login'))
        .to.eql(true, 'expected clear login action to be dispatched');
    });
  });

  describe('refreshAuthentication', () => {
    const user = 'user-1';
    const token = 'token-23';

    beforeEach(() => {
      checkToken.returns(Promise.resolve(true));
      sessionStorage.getItem.returns(
        JSON.stringify({user, token})
      );
    });

    it('should get login from session storage', () => {
      refreshAuthentication();

      expect(sessionStorage.getItem.calledWith(LOGIN_KEY)).to.eql(true);
    });

    it('should dispatch login action on success', (done) => {
      refreshAuthentication()
        .then(() => {
          expect(createLoginAction.calledWith(user, token))
            .to.eql(true, `expected login action to be created with user: ${user} and token: ${token}`);
          expect(dispatchAction.calledWith('login-action'))
            .to.eql(true, 'expected login action to be dispatched');

          done();
        });

      Promise.runAll();
    });
  });

  describe('login', () => {
    const user = 'user-34';
    const password = 'pass-67d45';
    const token = '@tokenik-3';
    let response;

    beforeEach(() => {
      authenticate.returns(Promise.resolve(token));
      response = login(user, password);
      Promise.runAll();
    });

    it('should authenticate with user and password', () => {
      expect(authenticate.calledWith(user, password)).to.eql(true);
    });

    it('should add login item to session storage', () => {
      expect(sessionStorage.setItem.calledWith(
        LOGIN_KEY,
        JSON.stringify(
          {
            user,
            token
          }
        )
      )).to.eql(true);
    });

    it('should dispatch login action', () => {
      expect(createLoginAction.calledWith(user, token))
        .to.eql(true, `expected login action to be created with user: ${user} and token: ${token}`);
      expect(dispatchAction.calledWith('login-action'))
        .to.eql(true, 'expected login action to be dispatched');
    });

    it('should return successful promise', (done) => {
      response.then(done);

      Promise.runAll();
    });

    it('should not catch failed authentication promise', (done) => {
      authenticate.returns(Promise.reject());

      login(user, password)
        .catch(done);

      Promise.runAll();
    });
  });
});
