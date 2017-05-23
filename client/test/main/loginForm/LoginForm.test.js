import {expect} from 'chai';
import {jsx} from 'view-utils';
import sinon from 'sinon';
import {mountTemplate, selectByText, triggerEvent} from 'testHelpers';
import {LoginForm, __set__, __ResetDependency__} from  'main/loginForm/LoginForm';

describe('<LoginForm>', () => {
  const selector = 'loginForm';
  let node;
  let update;
  let performLogin;
  let passwordInput;
  let userInput;
  let loginButton;
  let router;
  let getLogin;

  beforeEach(() => {
    performLogin = sinon.spy();
    __set__('performLogin', performLogin);

    router = {
      goTo: sinon.spy()
    };
    __set__('router', router);

    getLogin = sinon.stub();
    __set__('getLogin', getLogin);

    ({node, update} = mountTemplate(<LoginForm selector={selector}/>));

    passwordInput = getFieldByText('Password');
    userInput = getFieldByText('User');
    loginButton = node.querySelector('button[type="submit"]');
  });

  afterEach(() => {
    __ResetDependency__('performLogin');
    __ResetDependency__('router');
    __ResetDependency__('getLogin');
  });

  it('should render form login element', () => {
    expect(node.querySelector('form.form-signin')).to.exist;
  });

  it('should render user field', () => {
    expect(userInput).to.exist;
  });

  it('should render password field', () => {
    expect(passwordInput).to.exist;
  });

  it('should render submit button', () => {
    expect(loginButton).to.exist;
  });

  it('should render "Login" as Button label', () => {
    update({
      [selector]: {
        inProgress: false
      }
    });

    expect(loginButton.innerText).to.eql('Login');
  });

  it('should render a loading indicator when login is in progress', () => {
    update({
      [selector]: {
        inProgress: true
      }
    });

    expect(loginButton.querySelector('.glyphicon')).to.exist;
  });

  it('should not display error message by default', () => {
    expect(node).not.to.contain.text('Login incorrect. Check username / password.');
  });

  it('should display error message when error is true on state', () => {
    update({
      [selector]: {
        error: true
      }
    });

    expect(node).to.contain.text('Could not login. Check username / password.');
  });

  it('should performLogin with user and password on submit', () => {
    const user = 'u1';
    const password = 'p1';

    userInput.value = user;
    passwordInput.value = password;

    triggerEvent({
      node,
      selector: 'form',
      eventName: 'submit'
    });

    expect(performLogin.calledWith(user, password)).to.eql(true);
  });

  it('should disable login button and inputs when login is in Progress', () => {
    expect(loginButton.getAttribute('disabled')).to.eql(null, 'expected login button to not be disabled');
    expect(userInput.getAttribute('disabled')).to.eql(null, 'expected user input to not be disabled');
    expect(passwordInput.getAttribute('disabled')).to.eql(null, 'expected password input to not be disabled');

    update({
      [selector]: {
        inProgress: true
      }
    });

    expect(loginButton.getAttribute('disabled')).to.eql('true', 'expected login button to be disabled');
    expect(userInput.getAttribute('disabled')).to.eql('true', 'expected user input to be disabled');
    expect(passwordInput.getAttribute('disabled')).to.eql('true', 'expected password input to be disabled');
  });

  it('should enable login button and inputs when login is no longer in Progress', () => {
    update({
      [selector]: {
        inProgress: true
      }
    });

    expect(loginButton.getAttribute('disabled')).to.eql('true', 'expected login button to be disabled');
    expect(userInput.getAttribute('disabled')).to.eql('true', 'expected user input to be disabled');
    expect(passwordInput.getAttribute('disabled')).to.eql('true', 'expected password input to be disabled');

    update({
      [selector]: {
        inProgress: false
      }
    });

    expect(loginButton.getAttribute('disabled')).to.eql(null, 'expected login button to not be disabled');
    expect(userInput.getAttribute('disabled')).to.eql(null, 'expected user input to not be disabled');
    expect(passwordInput.getAttribute('disabled')).to.eql(null, 'expected password input to not be disabled');
  });

  it('should focus the password field when the login fails', () => {
    update({
      [selector]: {
        error: true
      }
    });

    expect(document.activeElement).to.eql(passwordInput);
  });

  it('should redirect to default view if user is logged in', () => {
    getLogin.returns('something');

    update({
      [selector]: {
        inProgress: false
      }
    });

    expect(router.goTo.calledWith('default')).to.eql(true);
  });

  it('should not redirect to default view if user is not logged in', () => {
    getLogin.returns(false);

    update({
      [selector]: {
        inProgress: false
      }
    });

    expect(router.goTo.called).to.eql(false);
  });

  function getFieldByText(text) {
    const [section] = selectByText(
      node.querySelectorAll('.form-group'),
      text
    );

    return section.querySelector('input');
  }
});
