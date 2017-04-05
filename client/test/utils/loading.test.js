import {expect} from 'chai';
import {addLoading, createLoadingActionFunction, createResultActionFunction, createResetActionFunction,
        INITIAL_STATE, LOADING_STATE, LOADED_STATE} from 'utils/loading';
import sinon from 'sinon';

describe('loading', () => {
  let reducer;
  let originalReducer;

  const LOADING_PROPERTY = 'testLoading';
  const DATA = 'someData';

  beforeEach(() => {
    originalReducer = sinon.stub().returnsArg(0);

    reducer = addLoading(originalReducer, LOADING_PROPERTY);
  });

  it('should not modify original state when original reducer returns state', () => {
    const state = {};
    const reducer = addLoading(x => x, LOADING_PROPERTY);

    reducer(state, {type: '@@INIT'});

    expect(state).to.eql({});
  });

  it('should allow original reducer to create default state', () => {
    const originalReducer = (state = {a: 1}) => state;
    const reducer = addLoading(originalReducer, LOADING_PROPERTY);
    const state = reducer(undefined, {type: '@@INIT'});

    expect(state).to.eql({
      [LOADING_PROPERTY]: {
        state: INITIAL_STATE
      },
      a: 1
    });
  });

  it('creates an initial state for property', () => {
    const state = reducer(undefined, {type: '@@INIT'});

    expect(state[LOADING_PROPERTY].state).to.eql(INITIAL_STATE);
  });

  it('creates function to create a loading action', () => {
    const fct = createLoadingActionFunction(LOADING_PROPERTY);
    const action = fct();

    expect(action.type).to.be.defined;
  });

  it('creates a function to create a result action', () => {
    const fct = createResultActionFunction(LOADING_PROPERTY);
    const action = fct(DATA);

    expect(action.type).to.be.defined;
    expect(action.result).to.eql(DATA);
  });

  it('creates a function to create a reset action', () => {
    const fct = createResetActionFunction(LOADING_PROPERTY);
    const action = fct(DATA);

    expect(action.type).to.be.defined;
  });

  it('updates the state when processing a loading action', () => {
    const state = reducer(undefined, createLoadingActionFunction(LOADING_PROPERTY)());

    expect(state[LOADING_PROPERTY].state).to.eql(LOADING_STATE);
  });

  it('updates the state when processing a loaded action', () => {
    const state = reducer(undefined, createResultActionFunction(LOADING_PROPERTY)(DATA));

    expect(state[LOADING_PROPERTY].state).to.eql(LOADED_STATE);
    expect(state[LOADING_PROPERTY].data).to.eql(DATA);
  });

  it('updates the state when processing a reset action', () => {
    const state = reducer({[LOADING_PROPERTY]: {state: LOADED_STATE, data: 'ABC'}}, createResetActionFunction(LOADING_PROPERTY)());

    expect(state[LOADING_PROPERTY].state).to.eql(INITIAL_STATE);
  });

  it('calls the original reducer', () => {
    const state = {};
    const action = {type: '@@INIT'};

    reducer(state, action);

    expect(originalReducer.getCall(0).args[1]).to.eql(action);
  });
});
