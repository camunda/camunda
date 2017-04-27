import {expect} from 'chai';
import {reducer, createSetHeightAction, SET_HEIGHT} from 'main/processDisplay/statistics/reducer';

describe('statistics reducer', () => {
  it('should create a set height action', () => {
    const action = createSetHeightAction(1234);

    expect(action.type).to.eql(SET_HEIGHT);
    expect(action.height).to.eql(1234);
  });

  it('should store the height in the state', () => {
    const state = reducer(undefined, createSetHeightAction(1234));

    expect(state.height).to.eql(1234);
  });
});
