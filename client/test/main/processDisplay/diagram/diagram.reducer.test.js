import {expect} from 'chai';
import {reducer} from 'main/processDisplay/diagram';

describe('diagram reducer', () => {
  it('should set diagram property on state', () => {
    const state = reducer(undefined, {type: '@@INIT'});

    expect(state.diagram).to.exist;
    expect(typeof state.diagram).to.eql('string');
  });
});
