import {expect} from 'chai';
import {filterType as executedNodeType} from 'main/processDisplay/controls/filter/executedNode';
import {getFilterQuery} from 'main/processDisplay/query';

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
        data: ['a', 'String', '=', [123]]
      }
    ]);

    expect(response.variables).to.exist;
    expect(response.variables).to.have.a.lengthOf(1);
    expect(response.variables[0].name).to.eql('a');
    expect(response.variables[0].type).to.eql('String');
    expect(response.variables[0].operator).to.eql('=');
    expect(response.variables[0].values[0]).to.eql(123);
  });

  it('should add executed nodes filter entries', () => {
    const response = getFilterQuery([
      {
        type: executedNodeType,
        data: [
          {
            id: 'd1'
          },
          {
            id: 'd2'
          }
        ]
      },
      {
        type: executedNodeType,
        data: [
          {
            id: 'c1'
          }
        ]
      }
    ]);

    expect(response.executedFlowNodes).to.eql({
      andLinkedIds: [
        {
          orLinkedIds: ['d1', 'd2']
        },
        {
          orLinkedIds: ['c1']
        }
      ]
    });
  });
});
