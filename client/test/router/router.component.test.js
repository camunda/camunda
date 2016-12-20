import {expect} from 'chai';
import {mountTemplate} from 'testHelpers/mountTemplate';
import {jsx} from 'view-utils';
import sinon from 'sinon';
import {Router, getLastRoute, __set__, __ResetDependency__} from 'router/router.component';

describe('<Router>', () => {
  let node;
  let update;
  let eventsBus;
  let childUpdate;
  let ChildComp;

  beforeEach(() => {
    childUpdate = sinon.spy();
    ChildComp = () => {
      return () => childUpdate
    };

    ({node, update, eventsBus} = mountTemplate(<Router routerProperty="router"><ChildComp/></Router>));
  });

  it('should set lastRoute on update', () => {
    const route = 'route';

    update({router: {route}});

    expect(getLastRoute()).to.equal(route);
  });

  it('should call children update with state on update', () => {
    const state = {a: 1, router: {childState: 2}};

    update(state);

    expect(childUpdate.calledOnce).to.eql(true);
    expect(childUpdate.calledWith(state)).to.eql(true);
  });
});
