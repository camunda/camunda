import {expect} from 'chai';
import sinon from 'sinon';
import {createMockComponent, mountTemplate} from 'testHelpers';
import {jsx} from 'view-utils';
import {Authenticated, __set__, __ResetDependency__} from 'login/authenticated.component';

describe('<Authenticated>', () => {
  const redirectRoute = 'redirect-route';
  let router;
  let lastRoute;
  let getLastRoute;
  let getLogin;
  let Child;
  let node;
  let update;
  let eventsBus;

  beforeEach(() => {
    router = {
      goTo: sinon.spy()
    };
    __set__('router', router);

    lastRoute = {
      name: 'route-a',
      params: {b: 1}
    };
    getLastRoute = sinon.stub().returns(lastRoute);
    __set__('getLastRoute', getLastRoute);

    getLogin = sinon.stub();
    __set__('getLogin', getLogin);

    Child = createMockComponent('child');

    ({node, update, eventsBus} = mountTemplate(<Authenticated routeName={redirectRoute}>
      <Child/>
    </Authenticated>));
  });

  afterEach(() => {
    __ResetDependency__('router');
    __ResetDependency__('getLastRoute');
    __ResetDependency__('getLogin');
  });

  it('should create div empty element', () => {
    expect(node.children.length).to.eql(1, 'expected only one element to be created');
    expect(node.children[0].tagName.toLowerCase()).to.eql('div');
    expect(node.children[0].innerHTML).to.match(/^\s*$/);
  });

  it('should redirect route when login is false', () => {
    getLogin.returns(false);

    update({});

    expect(
      router.goTo.calledWith(
        redirectRoute,
        {
          name: lastRoute.name,
          params: JSON.stringify(lastRoute.params)
        },
        true
      )
    ).to.eql(true);
  });

  describe('when login is truthy', () => {
    const state = 'state';

    beforeEach(() => {
      getLogin.returns(true);

      update(state);
    });

    it('should pass state to children update', () => {
      expect(Child.mocks.update.calledWith(state)).to.eql(true);
    });

    it('should pass events to children', () => {
      const data = 'data-123';
      const event = 'event-1';
      const listener = sinon.spy();
      const childEventsBus = Child.getEventsBus(0);

      childEventsBus.on(event, listener);

      eventsBus.fireEvent(event, data);

      expect(listener.calledOnce).to.eql(true, 'expected listener to be called once');
      expect(listener.calledWith({
        name: event,
        stopped: false,
        data
      })).to.eql(true, `expected listener to be called with ${event}`);
    });

    it('should update children with new state without recreating them', () => {
      const state = 'new-state';

      update(state);

      expect(Child.mocks.template.calledOnce).to.eql(true, 'expected Child template to be called only once');
      expect(Child.mocks.update.calledWith(state)).to.eql(true, 'expected Child to be updated with new state');
    });

    it('should do nothing when login is being checked', () => {
      Child.mocks.template.calls = [];
      getLogin.returns({
        check: true
      });

      update(state);

      expect(Child.mocks.template.called).to.eql(false, 'expected template not to be rendered');
      expect(router.goTo.called).to.eql(false, 'expected route not to be redirected');
    });
  });
});
