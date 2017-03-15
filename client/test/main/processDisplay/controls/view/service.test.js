import {expect} from 'chai';
import sinon from 'sinon';
import {setView, __set__, __ResetDependency__} from 'main/processDisplay/controls/view/service';

describe('View service', () => {
  const SET_ACTION = 'SET_ACTION';

  let dispatchAction;
  let createSetViewAction;

  beforeEach(() => {
    dispatchAction = sinon.spy();
    __set__('dispatchAction', dispatchAction);

    createSetViewAction = sinon.stub().returns(SET_ACTION);
    __set__('createSetViewAction', createSetViewAction);
  });

  afterEach(() => {
    __ResetDependency__('dispatchAction');
    __ResetDependency__('createSetViewAction');
  });

  describe('set view', () => {
    const viewValue = 'fake-view';

    beforeEach(() => {
      setView(viewValue);
    });

    it('should dispatch set view action', () => {
      expect(dispatchAction.calledWith(SET_ACTION)).to.eql(true);
    });

    it('should create action with provided view value', () => {
      expect(createSetViewAction.calledWith(viewValue)).to.eql(true);
    });
  });
});
