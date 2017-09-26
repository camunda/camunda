import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import sinon from 'sinon';
import {LoginFormReact, __set__, __ResetDependency__} from  'main/loginForm/LoginForm';
import React from 'react';
import {mount} from 'enzyme';

chai.use(chaiEnzyme());

const {expect} = chai;
const jsx = React.createElement;

describe('<LoginForm>', () => {
  let wrapper;
  let performLogin;
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
  });

  afterEach(() => {
    __ResetDependency__('performLogin');
    __ResetDependency__('router');
    __ResetDependency__('getLogin');
  });

  describe('basic view', () => {
    beforeEach(() => {
      wrapper = mount(<LoginFormReact inProgress={false} error={false} />);
    });

    it('should have user input field', () => {
      expect(wrapper.find('input[type="text"].user')).to.exist;
    });

    it('should have password input field', () => {
      expect(wrapper.find('input[type="password"].password')).to.exist;
    });

    it('should have submit button', () => {
      expect(wrapper.find('button[type="submit"]')).to.exist;
    });

    it('should not have error', () => {
      expect(wrapper.find('.text-danger')).not.to.exist;
    });

    it('should not have spinner in login button', () => {
      expect(wrapper.find('button[type="submit"] .spin')).not.to.exist;
    });

    it('should perform login action', () => {
      wrapper.find('.user').simulate('change', {
        target: {
          value: 'user1'
        }
      });
      wrapper.find('.password').simulate('change', {
        target: {
          value: 'pass1'
        }
      });
      wrapper.find('form').simulate('submit');

      expect(performLogin.calledWith('user1', 'pass1')).to.eql(true);
    });
  });

  describe('error view', () => {
    beforeEach(() => {
      wrapper = mount(<LoginFormReact inProgress={false} error={true} />);
    });

    it('should display error message', () => {
      expect(wrapper.find('.text-danger')).to.exist;
      expect(wrapper).to.contain.text('Could not login. Check username / password.');
    });
  });

  describe('in progress view', () => {
    beforeEach(() => {
      wrapper = mount(<LoginFormReact inProgress={true} error={false} />);
    });

    it('should disable user field', () => {
      expect(wrapper.find('.user')).to.be.disabled();
    });

    it('should disable password field', () => {
      expect(wrapper.find('.password')).to.be.disabled();
    });

    it('should disable login button', () => {
      expect(wrapper.find('button[type="submit"]')).to.be.disabled();
    });

    it('should add spinner to login button', () => {
      expect(wrapper.find('button[type="submit"] .spin')).to.exist;
    });
  });
});
