import {expect} from 'chai';
import {jsx} from 'view-utils';
import sinon from 'sinon';
import {mountTemplate, selectByText, triggerEvent} from 'testHelpers';
import {LoginForm, __set__, __ResetDependency__} from  'main/loginForm/loginForm.component';

describe('<LoginForm>', () => {
  const selector = 'loginForm';
  let node;
  let update;
  let performLogin;
  let changePassword;
  let changeUser;
  let passwordInput;
  let userInput;
  let loginButton;

  beforeEach(() => {
    performLogin = sinon.spy();
    __set__('performLogin', performLogin);

    changePassword = sinon.spy();
    __set__('changePassword', changePassword);

    changeUser = sinon.spy();
    __set__('changeUser', changeUser);

    ({node, update} = mountTemplate(<LoginForm selector={selector}/>));

    passwordInput = getFieldByText('Password');
    userInput = getFieldByText('User');
    loginButton = node.querySelector('button[type="submit"]');
  });

  afterEach(() => {
    __ResetDependency__('performLogin');
    __ResetDependency__('changePassword');
    __ResetDependency__('changeUser');
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

  it('should set user field on update', () => {
    const user = 'adam_s';

    update({
      [selector]: {
        user
      }
    });

    expect(userInput.value).to.eql(user);
  });

  it('should set password field on update', () => {
    const password = 'adam_s';

    update({
      [selector]: {
        password
      }
    });

    expect(passwordInput.value).to.eql(password);
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

  it('should call changeUser when user field changes', () => {
    const value = 'pass1';
    const userNode = node.querySelector('input[type="text"]');

    userNode.value = value;

    triggerEvent({
      node: userNode,
      eventName: 'keyup',
    });

    expect(changeUser.calledOnce).to.eql(true, 'expected changeUser to be called once');
    expect(changeUser.calledWith(value)).to.eql(true, 'expected changeUser called with ' + value);
  });

  it('should call changePassword when password field changes', () => {
    const value = 'pass1';
    const passNode = node.querySelector('input[type="password"]');

    passNode.value = value;

    triggerEvent({
      node: passNode,
      eventName: 'keyup',
    });

    expect(changePassword.calledOnce).to.eql(true, 'expected changePassword to be called once');
    expect(changePassword.calledWith(value)).to.eql(true, 'expected changePassword called with ' + value);
  });

  it('should performLogin with user and password on submit', () => {
    const user = 'u1';
    const password = 'p1';

    update({
      [selector]: {
        user,
        password
      }
    });

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

  function getFieldByText(text) {
    const [section] = selectByText(
      node.querySelectorAll('.form-group'),
      text
    );

    return section.querySelector('input');
  }
});
