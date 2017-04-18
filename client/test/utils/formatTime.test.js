import {expect} from 'chai';
import {formatTime, ms, d, m, s, h} from 'utils/formatTime';

describe('formatTime', () => {
  it('should format ms input into human readable string', () => {
    const time = 3 * d + 20 * m;

    expect(formatTime(time)).to.eql('3d&nbsp;20m');
  });

  it('should return string with only two biggest non-empty units of time', () => {
    const time = 3 * h + 20 * m + 10 * s + 11 * ms;

    expect(formatTime(time)).to.eql('3h&nbsp;20m');
  });

  it('should handle zero well', () => {
    expect(formatTime(0)).to.eql('0ms');
  });

  it('should single unit well', () => {
    expect(formatTime(5 * h)).to.eql('5h');
  });

  it('should return raw values if requested', () => {
    const result = formatTime(400, true);

    expect(result).to.be.an('array');
    expect(result[0].howMuch).to.eql(400);
    expect(result[0].name).to.eql('ms');
  });
});
