import {expect} from 'chai';
import {mountTemplate} from 'testHelpers/mountTemplate';
import {jsx} from 'view-utils';
import sinon from 'sinon';
import {Router, getLastRoute, __set__, __ResetDependency__} from 'router/Router';

describe('<Router>', () => {
  let update;
  let childUpdate;
  let ChildComp;
  let router;

  beforeEach(() => {
    router = {
      fireHistoryListeners: sinon.spy()
    };
    __set__('router', router);

    childUpdate = sinon.spy();
    ChildComp = () => {
      return () => childUpdate;
    };

    ({update} = mountTemplate(<Router selector="router"><ChildComp/></Router>));
  });

  afterEach(() => {
    __ResetDependency__('router');
  });

  it('should set lastRoute on update', () => {
    const route = 'route';

    update({router: {route}});

    expect(getLastRoute()).to.equal(route);
  });

  it('should fire history listeners with new route', () => {
    const route = 'route';

    update({router: {route}});

    expect(router.fireHistoryListeners.calledWith(route)).to.eql(true);
  });

  it('should call children update with state on update', () => {
    const state = {a: 1, router: {childState: 2}};

    update(state);

    expect(childUpdate.calledOnce).to.eql(true);
    expect(childUpdate.calledWith(state)).to.eql(true);
  });
});
