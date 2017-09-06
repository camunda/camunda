import {expect} from 'chai';
import sinon from 'sinon';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {jsx} from 'view-utils';
import {getRouter} from 'router';
import {Main, __set__, __ResetDependency__} from 'main/Main';

describe('<Main>', () => {
  let node;
  let update;
  let Header;
  let Footer;
  let DynamicLoader;
  let LoginForm;
  let Notifications;
  let router;

  beforeEach(() => {
    Header = createMockComponent('header-mock');
    Footer = createMockComponent('footer-mock');
    DynamicLoader = createMockComponent('dynamic-loader');
    LoginForm = createMockComponent('login-form');
    Notifications = createMockComponent('notifications');

    // Mocking components
    __set__('Header', Header);
    __set__('Footer', Footer);
    __set__('LoginForm', LoginForm);
    __set__('DynamicLoader', DynamicLoader);
    __set__('Notifications', Notifications);

    ({node, update} = mountTemplate(<Main />));

    router = getRouter();
    sinon.stub(router, 'goTo');
  });

  afterEach(() => {
    __ResetDependency__('Header');
    __ResetDependency__('Footer');
    __ResetDependency__('LoginForm');
    __ResetDependency__('DynamicLoader');
    __ResetDependency__('Notifications');

    router.goTo.restore();
  });

  it('should include header', () => {
    expect(node).to.contain.text(Header.text);
  });

  it('should include footer', () => {
    expect(node).to.contain.text(Footer.text);
  });

  it('should display notifications', () => {
    expect(node).to.contain.text(Notifications.text);
  });

  it('should have container element', () => {
    expect(node.querySelector('.site-wrap')).to.exist;
  });

  it('should have content element', () => {
    expect(node.querySelector('.page-wrap')).to.exist;
  });

  it('should display process selection view when last route and user is logged in', () => {
    update({
      router: {
        route: {
          name: 'default',
          params: {}
        }
      },
      login: {
        user: 's',
        token: 's'
      }
    });

    expect(node).to.contain.text(DynamicLoader.text);
    expect(DynamicLoader.appliedWith({
      module: 'processSelection'
    })).to.eql(true, 'expected DynamicLoader to be created with processSelection module');
  });

  it('should display the process display view', () => {
    update({
      router: {
        route: {
          name: 'processDisplay',
          params: {definition: 'foo'}
        }
      },
      login: {
        user: 's',
        token: 's'
      }
    });

    expect(node).to.contain.text(DynamicLoader.text);
    expect(DynamicLoader.appliedWith({
      module: 'processDisplay',
      selector: 'processDisplay'
    })).to.eql(true, 'expected DynamicLoader to be applied with processDisplay module');
  });

  it('should redirect to login route', () => {
    update({
      router: {
        route: {
          name: 'default',
          params: {}
        }
      }
    });

    expect(router.goTo.calledWith(
      'login',
      {
        name: 'default',
        params: '{}'
      }
    )).to.eql(true);
  });

  it('should display login form when route is login', () => {
    update({
      router: {
        route: {
          name: 'login',
          params: {}
        }
      }
    });

    expect(node).to.contain.text(LoginForm.text);
  });
});
