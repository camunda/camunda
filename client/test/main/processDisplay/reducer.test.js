import {expect} from 'chai';
import {reducer} from 'main/processDisplay/reducer';

describe('processDisplay reducer', () => {
  it('should produce state with correct properties', () => {
    const state = reducer(undefined, {type: '@@INIT'});

    expect(state.views).to.exist;
    expect(state.statistics).to.exist;
    expect(state.controls).to.exist;
  });
});
