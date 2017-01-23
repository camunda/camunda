import {expect} from 'chai';
import {reducer, createLoginErrorAction, createLoginInProgressAction} from 'main/loginForm/loginForm.reducer';

describe('loginForm reducer', () => {
  let inputState;

  beforeEach(() => {
    inputState = {
      extra: 'd',
      error: false,
      inProgress: false
    };
  });

  it('should not change input state on any action', () => {
    reducer(inputState, {
      type: 'blah'
    });
    reducer(inputState, createLoginErrorAction(true));
    reducer(inputState, createLoginErrorAction(false));

    expect(inputState).to.eql({
      extra: 'd',
      error: false,
      inProgress: false
    });
  });

  it('should change error on change error action', () => {
    const out1 = reducer(inputState, createLoginErrorAction(true));
    const out2 = reducer(out1, createLoginErrorAction(false));

    expect(out1).to.eql({
      ...inputState,
      error: true,
      inProgress: false
    }, 'expected error to be true');
    expect(out2).to.eql({
      ...inputState,
      error: false,
      inProgress: false
    }, 'expected error to be false');
  });

  it('should set inProgress property of state to true', () => {
    const outputState = reducer(inputState, createLoginInProgressAction());

    expect(outputState.inProgress).to.eql(true);
  });
});
