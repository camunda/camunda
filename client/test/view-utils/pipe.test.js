import {expect} from 'chai';
import {pipe} from 'view-utils';

describe('pipe', () => {
  it('should execute given functions in order passing result of last one as argument to next one', () => {
    const pipedFn = pipe(
      x => x + 1.5,
      x => x * 2,
      x => x - 1
    );

    expect(pipedFn(1)).to.eql(4);
    expect(pipedFn(3)).to.eql(8);
    expect(pipedFn(4)).to.eql(10);
  });
});
