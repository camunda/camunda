import {expect} from 'chai';
import {pipeReducers} from 'utils';

describe('utils pipeReducers', () => {
  let reducer;

  beforeEach(() => {
    reducer = pipeReducers(
      createArithmeticReducer(a => a + 1),
      createArithmeticReducer(a => a * 3),
      createArithmeticReducer(a => a + 2)
    );
  });

  it('should pipe given reducers', () => {
    const inputState = {a: 0};

    expect(reducer(inputState)).to.eql({
      a: 5
    });
  });

  it('should not modify input state', () => {
    const inputState = {a: 2};
    const outputState = reducer(inputState);

    expect(inputState).not.to.eql(outputState);
    expect(inputState).to.eql({a: 2});
  });

  function createArithmeticReducer(arithmeticFunction) {
    return (state = {a: 0}) => {
      return {
        ...state,
        a: arithmeticFunction(state.a)
      };
    };
  }
});
