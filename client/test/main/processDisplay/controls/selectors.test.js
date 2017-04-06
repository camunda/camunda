import {expect} from 'chai';
import sinon from 'sinon';
import {isViewSelected, __set__, __ResetDependency__} from 'main/processDisplay/controls/selectors';

describe('isViewSelected', () => {
  const targetView = 'TARGET';
  let getView;

  beforeEach(() => {
    getView = sinon.stub();
    __set__('getView', getView);
  });

  afterEach(() => {
    __ResetDependency__('getView');
  });

  it('should be true if the current view is the target view', () => {
    getView.returns(targetView);

    expect(isViewSelected(targetView)).to.be.true;
  });

  it('should be false if the current view is not the target view', () => {
    getView.returns('dd');

    expect(isViewSelected(targetView)).to.be.false;
  });

  it('should be true if the current view is in the target view array', () => {
    getView.returns(targetView);

    expect(isViewSelected(['a', 'b', targetView])).to.be.true;
  });

  it('should be false if the current view is not in the target view array', () => {
    getView.returns('dd');

    expect(isViewSelected(['a', 'b', 'c'])).to.be.false;
  });
});
