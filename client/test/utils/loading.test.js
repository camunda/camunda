import {expect} from 'chai';
import {DESTROY_EVENT} from 'view-utils';
import {
  addLoading, createLoadingActionFunction, createResultActionFunction,
  createResetActionFunction, createErrorActionFunction, addDestroyEventCleanUp,
  createLoadingReducer, INITIAL_STATE, LOADING_STATE, LOADED_STATE, ERROR_STATE
} from 'utils/loading';
import sinon from 'sinon';

describe('addLoading', () => {
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

  it('creates a function to create an error action', () => {
    const fct = createErrorActionFunction(LOADING_PROPERTY);
    const action = fct(DATA);

    expect(action.type).to.be.defined;
    expect(action.error).to.eql(DATA);
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

  it('updates the state when processing an error action', () => {
    const state = reducer({[LOADING_PROPERTY]: {state: LOADING_STATE}}, createErrorActionFunction(LOADING_PROPERTY)(DATA));

    expect(state[LOADING_PROPERTY].state).to.eql(ERROR_STATE);
    expect(state[LOADING_PROPERTY].error).to.eql(DATA);
  });

  it('calls the original reducer', () => {
    const state = {};
    const action = {type: '@@INIT'};

    reducer(state, action);

    expect(originalReducer.getCall(0).args[1]).to.eql(action);
  });
});

describe('addDestroyEventCleanUp', () => {
  let dispatch;
  let eventsBus;
  let names;

  beforeEach(() => {
    dispatch = sinon.spy();
    eventsBus = {
      on: sinon.stub().callsArg(1)
    };
    names = ['first', 'second'];

    addDestroyEventCleanUp(eventsBus, dispatch, ...names);
  });

  it('should add event listener for DESTROY_EVENT', () => {
    expect(eventsBus.on.calledWith(DESTROY_EVENT)).to.eql(true);
  });

  it('dispatch reset action for all names', () => {
    names.forEach(name => {
      expect(dispatch.calledWith({
        type: `RESET_${name.toUpperCase()}`,
        property: name
      })).to.eql(true, `expected reset action to be dispatch for ${name}`);
    });
  });
});

describe('createLoadingReducer', () => {
  const name = 'name';
  let reducer;

  beforeEach(() => {
    reducer = createLoadingReducer(name);
  });

  it('should return initial state for undefined', () => {
    expect(reducer(undefined, {type: '@@INIT'}))
      .to.eql({
        state: INITIAL_STATE
      });
  });

  it('should return initial state for undefined', () => {
    expect(reducer(undefined, {type: '@@INIT'}))
      .to.eql({
        state: INITIAL_STATE
      });
  });

  it('should return loading state on loading action', () => {
    expect(reducer({}, {type: 'LOAD_NAME'}))
      .to.eql({
        state: LOADING_STATE
      });
  });

  it('should return loaded state on loaded action', () => {
    const result = 'result';

    expect(reducer({}, {type: 'LOADED_NAME', result}))
      .to.eql({
        state: LOADED_STATE,
        data: result
      });
  });

  it('should return error state on error action', () => {
    const error = 'result';

    expect(reducer({}, {type: 'ERROR_NAME', error}))
      .to.eql({
        state: ERROR_STATE,
        error
      });
  });

  it('should return initial state on reset action', () => {
    const error = 'result';

    expect(reducer({}, {type: 'RESET_NAME', error}))
      .to.eql({
        state: INITIAL_STATE,
      });
  });
});
