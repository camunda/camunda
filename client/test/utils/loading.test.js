import {expect} from 'chai';
import {addLoading, createLoadingAction, createResultAction,
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

  it('creates an initial state for property', () => {
    const state = reducer(undefined, {type: '@@INIT'});

    expect(state[LOADING_PROPERTY].state).to.eql(INITIAL_STATE);
  });

  it('creates a loading action', () => {
    const action = createLoadingAction(LOADING_PROPERTY);

    expect(action.type).to.be.defined;
  });

  it('creates a result action', () => {
    const action = createResultAction(LOADING_PROPERTY, DATA);

    expect(action.type).to.be.defined;
    expect(action.result).to.eql(DATA);
  });

  it('updates the state when processing a loading action', () => {
    const state = reducer(undefined, createLoadingAction(LOADING_PROPERTY));

    expect(state[LOADING_PROPERTY].state).to.eql(LOADING_STATE);
  });

  it('updates the state when processing a loaded action', () => {
    const state = reducer(undefined, createResultAction(LOADING_PROPERTY, DATA));

    expect(state[LOADING_PROPERTY].state).to.eql(LOADED_STATE);
    expect(state[LOADING_PROPERTY].data).to.eql(DATA);
  });

  it('calls the original reducer', () => {
    const state = {};
    const action = {type: '@@INIT'};

    reducer(state, action);

    expect(originalReducer.calledWith(state, action)).to.eql(true);
  });
});
