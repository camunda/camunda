import {expect} from 'chai';
import sinon from 'sinon';
import {reducer} from 'main/processDisplay/processDisplay.reducer';

describe('processDisplay reducer', () => {
  it('should produce state with filters property', () => {
    const state = reducer(undefined, {type: '@@INIT'});

    expect(state).to.eql({
      filters: {
        filters: {}
      }
    });
  });
});
