import {expect} from 'chai';
import sinon from 'sinon';
import {setupPromiseMocking} from 'testHelpers';
import {clearLogin, refreshAuthentication, login, __set__, __ResetDependency__} from 'login/service';

describe('Login service', () => {
  const LOGIN_KEY = 'KEY-1';
  let dispatchAction;
  let localStorage;
  let createClearLoginAction;
  let createLoginAction;
  let post;
  let get;

  setupPromiseMocking();

  beforeEach(() => {
    __set__('LOGIN_KEY', LOGIN_KEY);

    dispatchAction = sinon.spy();
    __set__('dispatchAction', dispatchAction);

    localStorage = {
      removeItem: sinon.spy(),
      setItem: sinon.spy(),
      getItem: sinon.stub()
    };
    __set__('localStorage', localStorage);

    createClearLoginAction = sinon.stub().returns('clear-login');
    __set__('createClearLoginAction', createClearLoginAction);

    createLoginAction = sinon.stub().returns('login-action');
    __set__('createLoginAction', createLoginAction);

    post = sinon.stub();
    __set__('post', post);

    get = sinon.stub();
    __set__('get', get);
  });

  afterEach(() => {
    __ResetDependency__('LOGIN_KEY');
    __ResetDependency__('dispatchAction');
    __ResetDependency__('localStorage');
    __ResetDependency__('post');
    __ResetDependency__('get');
  });

  describe('clearLogin', () => {
    beforeEach(() => {
      get.returns(Promise.resolve(true));
      clearLogin();
    });

    it('should call the backend to clear the authentication', () => {
      expect(get.calledWith('/api/authentication/logout')).to.eql(true);
    });

    it('should remove login from session storage', () => {
      Promise.runAll();
      expect(localStorage.removeItem.calledWith(LOGIN_KEY)).to.eql(true);
    });

    it('should dispatch clear login action', () => {
      Promise.runAll();
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
      get.returns(Promise.resolve(true));
      localStorage.getItem.returns(
        JSON.stringify({user, token})
      );
    });

    it('should get login from session storage', () => {
      refreshAuthentication();

      expect(localStorage.getItem.calledWith(LOGIN_KEY)).to.eql(true);
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

    it('should add Authorization header with token', () => {
      refreshAuthentication();

      const [, , {headers}] = get.firstCall.args;

      expect(headers).to.eql({
        'X-Optimize-Authorization' : `Bearer ${token}`
      });
    });
  });

  describe('login', () => {
    const user = 'user-34';
    const password = 'pass-67d45';
    const token = '@tokenik-3';
    let response;

    beforeEach(() => {
      post.returns(Promise.resolve({
        text: sinon.stub().returns(
          Promise.resolve(token)
        )
      }));
      response = login(user, password);
      Promise.runAll();
    });

    it('should post with user and password', () => {
      expect(post.calledWith('/api/authentication', {
        username: user,
        password
      })).to.eql(true);
    });

    it('should add login item to session storage', () => {
      expect(localStorage.setItem.calledWith(
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
      post.returns(Promise.reject());

      login(user, password)
        .catch(done);

      Promise.runAll();
    });
  });
});
