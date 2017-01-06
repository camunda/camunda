import {expect} from 'chai';
import {mountTemplate} from 'testHelpers/mountTemplate';
import {jsx} from 'view-utils';
import sinon from 'sinon';
import {Router, getLastRoute} from 'router/router.component';

describe('<Router>', () => {
  let update;
  let childUpdate;
  let ChildComp;

  beforeEach(() => {
    childUpdate = sinon.spy();
    ChildComp = () => {
      return () => childUpdate;
    };

    ({update} = mountTemplate(<Router routerProperty="router"><ChildComp/></Router>));
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
