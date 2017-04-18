import {expect} from 'chai';
import {formatNumber} from 'utils/formatNumber';

const separator = '\u202F';

describe('formatNumber', () => {
  it('should return a string when given a number', () => {
    const result = formatNumber(32);

    expect(result).to.be.a('string');
  });

  it('should do nothing for numbers < 1000', () => {
    expect(formatNumber(4)).to.eql('4');
    expect(formatNumber(194)).to.eql('194');
  });

  it('should handle zero well', () => {
    expect(formatNumber(0)).to.eql('0');
  });

  it('should add thousand separator at correct position', () => {
    expect(formatNumber(6934)).to.eql(`6${separator}934`);
    expect(formatNumber(61934)).to.eql(`61${separator}934`);
    expect(formatNumber(761934)).to.eql(`761${separator}934`);
  });

  it('should add multiple thousand separators', () => {
    expect(formatNumber(2349875982)).to.eql(`2${separator}349${separator}875${separator}982`);
  });

  it('should allow using a custom separator', () => {
    expect(formatNumber(61934, '.')).to.eql('61.934');
  });
});
