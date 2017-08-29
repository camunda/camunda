import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import sinon from 'sinon';
import React from 'react';
import {mount} from 'enzyme';
import {AppMenu, __set__, __ResetDependency__} from 'main/header/appMenu/AppMenu';

chai.use(chaiEnzyme());

const {expect} = chai;
const jsx = React.createElement;

describe('<AppMenu>', () => {
  describe('default state', () => {
    let wrapper;

    beforeEach(() => {
      wrapper = mount(<AppMenu/>);
    });

    it('has no logout button', () => {
      expect(wrapper).to.not.contain.text('Logout');
    });
  });

  describe('logged in state', () => {
    let wrapper;
    let clearLogin;
    let getLogin;

    beforeEach(() => {
      clearLogin = sinon.spy();
      __set__('clearLogin', clearLogin);

      getLogin = sinon.stub().returns({
        user: 'u1',
        token: 'tk0'
      });
      __set__('getLogin', getLogin);

      wrapper = mount(<AppMenu />);
    });

    afterEach(() => {
      __ResetDependency__('clearLogin');
      __ResetDependency__('getLogin');
    });

    it('has a logout button', () => {
      expect(wrapper).to.contain.text('Logout');
    });

    it('calls clearLogin when clicked on Logout button', () => {
      wrapper.find({href: '#/login'}).simulate('click');

      expect(clearLogin.calledOnce).to.eql(true);
    });
  });
});
