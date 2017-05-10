import {expect} from 'chai';
import {getFilterQuery} from 'utils/query';

describe('getFilterQuery', () => {
  it('should convert a date filter to query entries', () => {
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

  it('should add variable filter entries', () => {
    const response = getFilterQuery([
      {
        type: 'variable',
        data: {
          name: 'a',
          operator: '=',
          value: 123
        }
      }
    ]);

    expect(response.variables).to.exist;
    expect(response.variables).to.have.a.lengthOf(1);
    expect(response.variables[0].name).to.eql('a');
    expect(response.variables[0].operator).to.eql('=');
    expect(response.variables[0].value).to.eql(123);
  });
});
