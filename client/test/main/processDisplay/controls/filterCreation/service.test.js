import {expect} from 'chai';
import sinon from 'sinon';
import {openModal, closeModal,
        __set__, __ResetDependency__} from 'main/processDisplay/controls/filterCreation/service';

describe('FilterCreation service', () => {
  const OPEN_ACTION = 'OPEN_MODAL';
  const CLOSE_ACTION = 'CLOSE_MODAL';

  let dispatchAction;
  let createOpenDateFilterModalAction;
  let createCloseDateFilterModalAction;

  beforeEach(() => {
    dispatchAction = sinon.spy();
    __set__('dispatchAction', dispatchAction);

    createOpenDateFilterModalAction = sinon.stub().returns(OPEN_ACTION);
    __set__('createOpenDateFilterModalAction', createOpenDateFilterModalAction);

    createCloseDateFilterModalAction = sinon.stub().returns(CLOSE_ACTION);
    __set__('createCloseDateFilterModalAction', createCloseDateFilterModalAction);
  });

  afterEach(() => {
    __ResetDependency__('dispatchAction');
    __ResetDependency__('createOpenDateFilterModalAction');
    __ResetDependency__('createCloseDateFilterModalAction');
  });

  describe('openModal', () => {
    beforeEach(() => {
      openModal();
    });
    it('should dispatch open modal action', () => {
      expect(dispatchAction.calledWith(OPEN_ACTION)).to.eql(true);
    });

    it('should create action', () => {
      expect(createOpenDateFilterModalAction.calledOnce).to.eql(true);
    });
  });

  describe('closeModal', () => {
    beforeEach(() => {
      closeModal();
    });
    it('should dispatch close modal action', () => {
      expect(dispatchAction.calledWith(CLOSE_ACTION)).to.eql(true);
    });

    it('should create action', () => {
      expect(createCloseDateFilterModalAction.calledOnce).to.eql(true);
    });
  });
});
