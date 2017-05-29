import {expect} from 'chai';
import {reducer,
  createSelectVariableIdxAction,
  createSetOperatorAction,
  createSetValueAction
} from 'main/processDisplay/controls/filter/variable/reducer';

describe('variable reducer', () => {
  it('should set the selected index when handling select variable action', () => {
    const action = createSelectVariableIdxAction(4);
    const state = reducer(undefined, action);

    expect(state.selectedIdx).to.eql(4);
  });

  it('should reset the operator and value when changing the selected variable', () => {
    const action = createSelectVariableIdxAction(4);
    const state = reducer({
      selectedIdx: 123,
      operator: 'OP',
      value: 'AAAAAAA'
    }, action);

    expect(state.operator).to.eql('=');
    expect(state.value).to.eql('');
  });

  it('should set the operator field', () => {
    const action = createSetOperatorAction('NEW_OP');
    const state = reducer(undefined, action);

    expect(state.operator).to.eql('NEW_OP');
  });

  it('should set the variable value', () => {
    const action = createSetValueAction('NEW_VAL');
    const state = reducer(undefined, action);

    expect(state.value).to.eql('NEW_VAL');
  });
});
