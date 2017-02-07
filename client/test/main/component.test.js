import {expect} from 'chai';
import sinon from 'sinon';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {jsx} from 'view-utils';
import {getRouter} from 'router';
import {Main, __set__, __ResetDependency__} from 'main/component';

describe('<Main>', () => {
  let node;
  let update;
  let Header;
  let Footer;
  let DynamicLoader;
  let LoginForm;
  let router;

  beforeEach(() => {
    Header = createMockComponent('header-mock');
    Footer = createMockComponent('footer-mock');
    DynamicLoader = createMockComponent('dynamic-loader');
    LoginForm = createMockComponent('login-form');

    // Mocking components
    __set__('Header', Header);
    __set__('Footer', Footer);
    __set__('LoginForm', LoginForm);
    __set__('DynamicLoader', DynamicLoader);

    ({node, update} = mountTemplate(<Main />));

    router = getRouter();
    sinon.spy(router, 'goTo');
  });

  afterEach(() => {
    __ResetDependency__('Header');
    __ResetDependency__('Footer');
    __ResetDependency__('LoginForm');
    __ResetDependency__('DynamicLoader');

    router.goTo.restore();
  });

  it('should include header', () => {
    expect(node).to.contain.text(Header.text);
  });

  it('should include footer', () => {
    expect(node).to.contain.text(Footer.text);
  });

  it('should have container element', () => {
    expect(node.querySelector('.site-wrap')).to.exist;
  });

  it('should have content element', () => {
    expect(node.querySelector('.page-wrap')).to.exist;
  });

  it('should display dynamic loader view when last route and user is logged in', () => {
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
    expect(DynamicLoader.calledWith({
      children: [],
      module: 'processDisplay',
      selector: 'processDisplay'
    })).to.eql(true, 'expected DynamicLoader to be created with processDisplay module');
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
