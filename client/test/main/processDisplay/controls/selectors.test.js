import {isViewSelected} from 'main/processDisplay/controls/selectors';
import {expect} from 'chai';

describe('is view selected', () => {
  const targetView = 'TARGET';

  it('should be true if the current view is the target view', () => {
    expect(isViewSelected({view: targetView}, targetView)).to.be.true;
  });

  it('should be false if the current view is not the target view', () => {
    expect(isViewSelected({view: 'something else'}, targetView)).to.be.false;
  });

  it('should be true if the current view is in the target view array', () => {
    expect(isViewSelected({view: targetView}, ['a', 'b', targetView])).to.be.true;
  });

  it('should be false if the current view is not in the target view array', () => {
    expect(isViewSelected({view: targetView}, ['a', 'b', 'c'])).to.be.false;
  });
});
