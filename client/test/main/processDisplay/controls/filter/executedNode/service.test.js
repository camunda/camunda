import {expect} from 'chai';
import sinon from 'sinon';
import {addFlowNodesFilter, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/executedNode/service';

describe('main/processDisplay/controls/filter/executedNode/service addFlowNodesFilter', () => {
  const action = 'action-1';
  let dispatch;
  let createAddFlowNodesFilterAction;

  beforeEach(() => {
    dispatch = sinon.spy();
    createAddFlowNodesFilterAction = sinon.stub().returns(action);

    __set__('dispatch', dispatch);
    __set__('createAddFlowNodesFilterAction', createAddFlowNodesFilterAction);
  });

  afterEach(() => {
    __ResetDependency__('dispatch');
    __ResetDependency__('createAddFlowNodesFilterAction');
  });

  it('should dispatch change selected nodes action', () => {
    const selected = 'dd';

    addFlowNodesFilter(selected);

    expect(createAddFlowNodesFilterAction.calledWith(selected)).to.eql(true);
    expect(dispatch.calledWith(action)).to.eql(true);
  });
});
