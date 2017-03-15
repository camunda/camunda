import {expect} from 'chai';
import {reducer, createSetViewAction} from 'main/processDisplay/controls/view/reducer';

describe('View reducer', () => {
  it('should set the "none" state per default', () => {
    const state = reducer(undefined, {type: 'WHATEVER'});

    expect(state).to.eql('none');
  });

  it('should set the mode as state', () => {
    const mode = 'fake-mode';
    const state = reducer(undefined, createSetViewAction(mode));

    expect(state).to.eql(mode);
  });
});
