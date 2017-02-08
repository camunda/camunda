import {expect} from 'chai';
import {mountTemplate, selectByText, triggerEvent} from 'testHelpers';
import sinon from 'sinon';
import {jsx} from 'view-utils';
import {AppMenu, __set__, __ResetDependency__} from 'main/header/appMenu/AppMenu';

describe('<AppMenu>', () => {
  describe('default state', () => {
    let node;

    beforeEach(() => {
      ({node} = mountTemplate(<AppMenu/>));
    });

    it('has no logout button', () => {
      expect(node).to.not.contain.text('Logout');
    });
  });

  describe('logged in state', () => {
    let node;
    let update;
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

      ({node, update} = mountTemplate(<AppMenu/>));
      update(getLogin());
    });

    afterEach(() => {
      __ResetDependency__('clearLogin');
      __ResetDependency__('getLogin');
    });

    it('has a logout button', () => {
      expect(node).to.contain.text('Logout');
    });

    it('calls clearLogin when clicked on Logout button', () => {
      const [logoutBtn] = selectByText(
        node.querySelectorAll('a'),
        'Logout'
      );

      triggerEvent({
        node: logoutBtn,
        eventName: 'click'
      });

      expect(clearLogin.calledOnce).to.eql(true);
    });
  });
});
