import {expect} from 'chai';
import {reducer, createCreateStartDateFilterAction,
        createDeleteFilterAction} from 'main/processDisplay/controls/filter/reducer';

describe('Filter reducer', () => {
  it('should delete a filter', () => {
    const filter = {
      type: 'someType',
      data: {
        some: 'very',
        important: 'data'
      }
    };
    const state = reducer([filter], createDeleteFilterAction({
      some: 'very',
      important: 'data'
    }));

    expect(state.length).to.eql(0);
  });

  describe('date filter', () => {
    const start = '2016-12-01T00:00:00';
    const end = '2016-12-31T23:59:59';
    let type;
    let data;

    it('should set a date filter', () => {
      ([{type, data}] = reducer(undefined, createCreateStartDateFilterAction(start, end)));

      expect(type).to.eql('startDate');
      expect(data.start).to.eql(start);
      expect(data.end).to.eql(end);
    });

    it('should not have two date filters simultaniously', () => {
      const state = reducer(undefined, createCreateStartDateFilterAction(start, end));
      const newState = reducer(state, createCreateStartDateFilterAction(start, end));

      expect(newState.length).to.eql(1);
    });
  });
});
