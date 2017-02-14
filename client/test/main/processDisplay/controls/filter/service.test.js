import {expect} from 'chai';
import sinon from 'sinon';
import {openModal, closeModal, createStartDateFilter,
        __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/service';

describe('Filter service', () => {
  const OPEN_ACTION = 'OPEN_MODAL';
  const CLOSE_ACTION = 'CLOSE_MODAL';
  const CREATE_FILTER_ACTION = 'CREATE_FILTER_ACTION';

  let dispatchAction;
  let createOpenDateFilterModalAction;
  let createCloseDateFilterModalAction;
  let createCreateStartDateFilterAction;

  beforeEach(() => {
    dispatchAction = sinon.spy();
    __set__('dispatchAction', dispatchAction);

    createOpenDateFilterModalAction = sinon.stub().returns(OPEN_ACTION);
    __set__('createOpenDateFilterModalAction', createOpenDateFilterModalAction);

    createCloseDateFilterModalAction = sinon.stub().returns(CLOSE_ACTION);
    __set__('createCloseDateFilterModalAction', createCloseDateFilterModalAction);

    createCreateStartDateFilterAction = sinon.stub().returns(CREATE_FILTER_ACTION);
    __set__('createCreateStartDateFilterAction', createCreateStartDateFilterAction);
  });

  afterEach(() => {
    __ResetDependency__('dispatchAction');
    __ResetDependency__('createOpenDateFilterModalAction');
    __ResetDependency__('createCloseDateFilterModalAction');
    __ResetDependency__('createCreateStartDateFilterAction');
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

  describe('createStartDateFilter', () => {
    const start = '2016-12-01T00:00:00';
    const end = '2016-12-31T23:59:59';

    beforeEach(() => {
      createStartDateFilter(start, end);
    });
    it('should dispatch open modal action', () => {
      expect(dispatchAction.calledWith(CREATE_FILTER_ACTION)).to.eql(true);
    });

    it('should create action', () => {
      expect(createCreateStartDateFilterAction.calledOnce).to.eql(true);
      expect(createCreateStartDateFilterAction.calledWith(start, end)).to.eql(true);
    });
  });
});
