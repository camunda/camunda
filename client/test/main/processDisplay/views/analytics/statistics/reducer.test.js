import {expect} from 'chai';
import {reducer, createSetHeightAction, SET_HEIGHT, __set__, __ResetDependency__} from 'main/processDisplay/views/analytics/statistics/reducer';

describe('statistics reducer', () => {
  let minHeight;
  let maxHeight;

  beforeEach(() => {
    minHeight = 100;
    maxHeight = 500;

    __set__('MIN_HEIGHT', minHeight);
    __set__('MAX_HEIGHT', maxHeight);
  });

  afterEach(() => {
    __ResetDependency__('MIN_HEIGHT');
    __ResetDependency__('MAX_HEIGHT');
  });

  it('should create a set height action', () => {
    const action = createSetHeightAction(1234);

    expect(action.type).to.eql(SET_HEIGHT);
    expect(action.height).to.eql(1234);
  });

  it('should store the height in the state', () => {
    const state = reducer(undefined, createSetHeightAction(300));

    expect(state.height).to.eql(300);
  });

  it('should not set the height to something larger than the maxvalue', () => {
    const state = reducer(undefined, createSetHeightAction(maxHeight + 1000));

    expect(state.height).to.not.be.greaterThan(maxHeight);
  });

  it('should not set the height to something smaller than the minvalue', () => {
    const state = reducer(undefined, createSetHeightAction(minHeight - 1000));

    expect(state.height).to.not.be.lessThan(minHeight);
  });
});
