import {expect} from 'chai';
import {mountTemplate} from 'testHelpers/mountTemplate';
import {jsx} from 'view-utils';
import sinon from 'sinon';
import {Router, getLastRoute, __set__, __ResetDependency__} from 'router/router.component';

describe('<Router>', () => {
  let node;
  let update;
  let eventsBus;
  let addChildren;
  let childrenUpdate;
  const children = 'children';

  beforeEach(() => {
    childrenUpdate = sinon.spy();
    addChildren = sinon
      .stub()
      .returns(childrenUpdate);

    __set__('addChildren', addChildren);

    ({node, update, eventsBus} = mountTemplate(<Router children={children}/>));
  });

  afterEach(() => {
    __ResetDependency__('addChildren');
  });

  it('should pass node, eventsBus and children to addChildren', () => {
    expect(addChildren.calledWith(node, eventsBus, children)).to.eql(true);
  });

  it('should set lastRoute on update', () => {
    const route = 'route';

    update({route});

    expect(getLastRoute()).to.equal(route);
  });

  it('should call children update with wtate on update', () => {
    const state = {a: 1, childState: 2};

    update(state);

    expect(childrenUpdate.calledOnce).to.eql(true);
    expect(childrenUpdate.calledWith(state)).to.eql(true);
  });
});
