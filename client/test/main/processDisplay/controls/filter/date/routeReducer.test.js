import {expect} from 'chai';
import {routeReducer, createCreateStartDateFilterAction} from 'main/processDisplay/controls/filter/date/routeReducer';

describe('Date-Filter route reducer', () => {
  const start = '2016-12-01T00:00:00';
  const end = '2016-12-31T23:59:59';
  let type;
  let data;

  it('should set a date filter', () => {
    ([{type, data}] = routeReducer(undefined, createCreateStartDateFilterAction(start, end)));

    expect(type).to.eql('startDate');
    expect(data.start).to.eql(start);
    expect(data.end).to.eql(end);
  });

  it('should not have two date filters simultaniously', () => {
    const state = routeReducer(undefined, createCreateStartDateFilterAction(start, end));
    const newState = routeReducer(state, createCreateStartDateFilterAction(start, end));

    expect(newState.length).to.eql(1);
  });
});
