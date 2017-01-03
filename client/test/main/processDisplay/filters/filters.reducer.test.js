import {expect} from 'chai';
import {reducer, createChangeFilterAction} from 'main/processDisplay/filters/filters.reducer';

describe('filters reducer', () => {
  it('should create default state with empty filters', () => {
    const state = reducer(undefined, {type: '@@INIT'});

    expect(state).to.eql({filters: {}});
  });

  it('should set filter on change filter action', () => {
    const newFilter = 'filter-value';
    const state = reducer(
      undefined,
      createChangeFilterAction('newFilter', newFilter)
    );

    expect(state).to.eql({
      filters: {
        newFilter
      }
    });
  });

  it('should set filter on change filter action and leave other filters unchanged', () => {
    const otherFilter = 'other-value';
    const newFilter = 'filter-value';
    const state = reducer(
      {
        filters: {otherFilter}
      },
      createChangeFilterAction('newFilter', newFilter)
    );

    expect(state).to.eql({
      filters: {
        newFilter,
        otherFilter
      }
    });
  });
});
