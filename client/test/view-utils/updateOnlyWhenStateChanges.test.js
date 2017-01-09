import {expect} from 'chai';
import {updateOnlyWhenStateChanges} from 'view-utils';
import sinon from 'sinon';

describe('updateOnlyWhenStateChanges', () => {
  let state1;
  let update;
  let wrappedUpdate;

  beforeEach(() => {
    state1 = {
      a: 1,
      b: 1
    };
    update = sinon.spy();
    wrappedUpdate = updateOnlyWhenStateChanges(update);

    wrappedUpdate(state1);

    update.reset();
  });

  it('should not update when state has not changed', () => {
    wrappedUpdate(state1);

    expect(update.called).to.eql(false);
  });

  it('should update when state has changed', () => {
    const state2 = {
      s: 1
    };

    wrappedUpdate(state2);

    expect(update.calledWith(state2)).to.eql(true);
    expect(update.calledOnce).to.eql(true);
  });
});
