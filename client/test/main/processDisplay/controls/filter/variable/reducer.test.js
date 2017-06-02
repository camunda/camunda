import {expect} from 'chai';
import sinon from 'sinon';
import {reducer,
  createSelectVariableIdxAction,
  createSetOperatorAction,
  createSetValueAction,
  createAddValueAction,
  createRemoveValueAction,
  __set__, __ResetDependency__
} from 'main/processDisplay/controls/filter/variable/reducer';

describe('variable reducer', () => {
  let operatorCanHaveMultipleValues;

  beforeEach(() => {
    operatorCanHaveMultipleValues = sinon.stub().returns(true);
    __set__('operatorCanHaveMultipleValues', operatorCanHaveMultipleValues);
  });

  afterEach(() => {
    __ResetDependency__('operatorCanHaveMultipleValues');
  });

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
      values: ['AAAAAAA']
    }, action);

    expect(state.operator).to.eql('=');
    expect(state.values).to.eql(['']);
  });

  it('should set the operator field', () => {
    const action = createSetOperatorAction('NEW_OP');
    const state = reducer(undefined, action);

    expect(state.operator).to.eql('NEW_OP');
  });

  it('should set the variable value', () => {
    const action = createSetValueAction('NEW_VAL');
    const state = reducer({values: ['']}, action);

    expect(state.values).to.eql(['NEW_VAL']);
  });

  it('should replace previous value when a new value is set', () => {
    const action = createSetValueAction('NEW_VAL', 1);
    const state = reducer({values: ['a', 'b', 'c']}, action);

    expect(state.values).to.contain('NEW_VAL');
    expect(state.values).to.not.contain('b');
  });

  it('should remove multiple values if the new operator does not allow multiple values', () => {
    operatorCanHaveMultipleValues.returns(false);

    const action = createSetOperatorAction('<');
    const state = reducer({values: ['a', 'b', 'c']}, action);

    expect(state.values).to.have.a.lengthOf(1);
  });

  it('should add a new value', () => {
    const action = createAddValueAction('NEW_VAL');
    const state = reducer({values: ['OLD_VAL']}, action);

    expect(state.values).to.contain('NEW_VAL');
  });

  it('should remove a value', () => {
    const action = createRemoveValueAction('a');
    const state = reducer({values: ['a', 'b', 'c']}, action);

    expect(state.values).to.not.contain('a');
  });
});
