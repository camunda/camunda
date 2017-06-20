import {expect} from 'chai';
import {formatDate, zeroCapNumber} from 'utils';

describe('utils formatDate', () => {
  it('should format date', () => {
    expect(
      formatDate(
        new Date(1959, 4, 12)
      )
    ).to.eql('1959-05-12');
  });
});

describe('utils zeroCapNumber', () => {
  it('should add zeros in front of number if it is shorter than given number of digits', () => {
    expect(
      zeroCapNumber(4, 12)
    ).to.eql('0012');
  });
});
