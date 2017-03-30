import {expect} from 'chai';
import {getFilterQuery} from 'utils/query';

describe('getFilterQuery', () => {
  it('should convert a date filter to to query entries', () => {
    const response = getFilterQuery([
      {
        type: 'startDate',
        data: {
          start: '2017-02-01T00:00:00',
          end: '2017-02-28T23:59:59'
        }
      }
    ]);

    expect(response.dates).to.exist;
    expect(response.dates).to.have.a.lengthOf(2);
    expect(response.dates[0].type).to.eql('start_date');
    expect(response.dates[1].type).to.eql('start_date');
  });
});
