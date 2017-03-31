import {expect} from 'chai';
import {runOnce} from 'utils/runOnce';
import sinon from 'sinon';

describe('runOnce', () => {
  let targetFn;
  let resultFn;

  beforeEach(() => {
    targetFn = sinon.spy();
    resultFn = runOnce(targetFn);
  });

  it('should allow function to run only once', () => {
    resultFn();
    resultFn();
    resultFn();

    expect(targetFn.calledOnce).to.eql(true);
  });

  it('passes arguments to target function', () => {
    resultFn(1, 2, 3);

    expect(targetFn.calledWith(1, 2, 3)).to.eql(true);
  });
});
