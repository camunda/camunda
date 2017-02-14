import {expect} from 'chai';
import {reducer, createOpenDateFilterModalAction, createCreateStartDateFilterAction,
        createCloseDateFilterModalAction} from 'main/processDisplay/controls/filter/reducer';

describe('Filter reducer', () => {
  let open;

  it('should set open to true on open action', () => {
    ({createModal: {open}} = reducer(undefined, createOpenDateFilterModalAction()));

    expect(open).to.eql(true);
  });

  it('should set open to false on open action', () => {
    ({createModal: {open}} = reducer(undefined, createCloseDateFilterModalAction()));

    expect(open).to.eql(false);
  });

  describe('date filter', () => {
    const start = '2016-12-01T00:00:00';
    const end = '2016-12-31T23:59:59';
    let type;
    let data;

    it('should set a date filter', () => {
      ({query: [{type, data}]} = reducer(undefined, createCreateStartDateFilterAction(start, end)));

      expect(type).to.eql('startDate');
      expect(data.start).to.eql(start);
      expect(data.end).to.eql(end);
    });

    it('should not have two date filters simultaniously', () => {
      const state = reducer(undefined, createCreateStartDateFilterAction(start, end));
      const newState = reducer(state, createCreateStartDateFilterAction(start, end));

      expect(newState.query.length).to.eql(1);
    });
  });
});
