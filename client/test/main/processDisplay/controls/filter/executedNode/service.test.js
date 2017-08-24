import {expect} from 'chai';
import sinon from 'sinon';
import {changeSelectedNodes, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/executedNode/service';

describe('main/processDisplay/controls/filter/executedNode/service changeSelectedNodes', () => {
  const action = 'action-1';
  let dispatch;
  let createChangeSelectNodesAction;

  beforeEach(() => {
    dispatch = sinon.spy();
    createChangeSelectNodesAction = sinon.stub().returns(action);

    __set__('dispatch', dispatch);
    __set__('createChangeSelectNodesAction', createChangeSelectNodesAction);
  });

  afterEach(() => {
    __ResetDependency__('dispatch');
    __ResetDependency__('createChangeSelectNodesAction');
  });

  it('should dispatch change selected nodes action', () => {
    const selected = 'dd';

    changeSelectedNodes(selected);

    expect(createChangeSelectNodesAction.calledWith(selected)).to.eql(true);
    expect(dispatch.calledWith(action)).to.eql(true);
  });
});
