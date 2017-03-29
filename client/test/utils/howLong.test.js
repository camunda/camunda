import {expect} from 'chai';
import {howLong, ms, d, m, s, h} from 'utils/howLong';

describe('howLong', () => {
  it('should format ms input into human readable string', () => {
    const time = 3 * d + 20 * m;

    expect(howLong(time)).to.eql('3d&nbsp;20m');
  });

  it('should return string with only two biggest non-empty units of time', () => {
    const time = 3 * h + 20 * m + 10 * s + 11 * ms;

    expect(howLong(time)).to.eql('3h&nbsp;20m');
  });
});
