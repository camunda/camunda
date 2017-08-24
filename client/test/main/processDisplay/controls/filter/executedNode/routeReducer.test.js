import {expect} from 'chai';
import {routeReducer, createChangeSelectNodesAction} from 'main/processDisplay/controls/filter/executedNode/routeReducer';

describe('main/processDisplay/controls/filter/executedNode routeReduce', () => {
  let initialState;

  beforeEach(() => {
    initialState = [
      {
        type: 'some-filter'
      }
    ];
  });

  it('should not change initial state', () => {
    routeReducer(initialState, createChangeSelectNodesAction('nodes'));
    routeReducer(initialState, {type: 'other-action'});

    expect(initialState).to.eql([
      {
        type: 'some-filter'
      }
    ]);
  });

  it('should add executedNode filter', () => {
    const resultState = routeReducer(initialState, createChangeSelectNodesAction('nodes'));

    expect(resultState).to.contain({
      type: 'some-filter'
    });
    expect(resultState).to.contain({
      type: 'executedNode',
      data: 'nodes'
    });
  });

  it('should not change result state when dealing with unknown action', () => {
    const resultState = routeReducer(initialState, {type: 'other-action'});

    expect(resultState).to.eql(initialState);
  });
});
