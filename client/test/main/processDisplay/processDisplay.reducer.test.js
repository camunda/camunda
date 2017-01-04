import {expect} from 'chai';
import {reducer} from 'main/processDisplay/processDisplay.reducer';

describe('processDisplay reducer', () => {
  it('should produce state with filters property', () => {
    const state = reducer(undefined, {type: '@@INIT'});

    expect(state.filters).to.eql({
      filters: {}
    });
  });

  it('should produce state with diagram property', () => {
    const state = reducer(undefined, {type: '@@INIT'});

    expect(state.diagram).to.exist;
  });
});
