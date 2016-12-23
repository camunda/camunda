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

  beforeEach(() => {
    performLogin = sinon.spy();
    __set__('performLogin', performLogin);

    changePassword = sinon.spy();
    __set__('changePassword', changePassword);

    changeUser = sinon.spy();
    __set__('changeUser', changeUser);

    ({node, update} = mountTemplate(<LoginForm selector={selector}/>));
  });

  afterEach(() => {
    __ResetDependency__('performLogin');
    __ResetDependency__('changePassword');
    __ResetDependency__('changeUser');
  });

  it('should render form login element', () => {
    expect(node.querySelector('form.login')).to.exist;
  });

  it('should render user field', () => {
    const [section] = selectByText(
      node.querySelectorAll('.login__section'),
      'user:'
    );

    expect(section).to.exist;
    expect(section.querySelector('input[type="text"]')).to.exist;
  });

  it('should render password field', () => {
    const [section] = selectByText(
      node.querySelectorAll('.login__section'),
      'password:'
    );

    expect(section).to.exist;
    expect(section.querySelector('input[type="password"]')).to.exist;
  });

  it('should render submit button', () => {
    const btn = node.querySelector('button[type="submit"]');

    expect(btn).to.exist;
    expect(btn.innerText).to.eql('Login');
  });

  it('should set user field on update', () => {
    const user = 'adam_s';

    update({
      [selector]: {
        user
      }
    });

    const [section] = selectByText(
      node.querySelectorAll('.login__section'),
      'user:'
    );
    const field = section.querySelector('input');

    expect(field.value).to.eql(user);
  });

  it('should set password field on update', () => {
    const password = 'adam_s';

    update({
      [selector]: {
        password
      }
    });

    const [section] = selectByText(
      node.querySelectorAll('.login__section'),
      'password:'
    );
    const field = section.querySelector('input');

    expect(field.value).to.eql(password);
  });

  it('should not display error message by default', () => {
    expect(node).not.to.contain.text('Incorrect login attempt! Calling cat police force!');
  });

  it('should display error message when error is true on state', () => {
    update({
      [selector]: {
        error: true
      }
    });

    expect(node).to.contain.text('Incorrect login attempt! Calling cat police force!');
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
});
