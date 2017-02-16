import {expect} from 'chai';
import sinon from 'sinon';
import {createStartDateFilter, formatDate, deleteFilter,
        __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/service';

describe('Filter service', () => {
  const CREATE_FILTER_ACTION = 'CREATE_FILTER_ACTION';
  const DELETE_FILTER = 'DELETE_FILTER';

  let dispatchAction;
  let createCreateStartDateFilterAction;
  let createDeleteFilterAction;

  beforeEach(() => {
    dispatchAction = sinon.spy();
    __set__('dispatchAction', dispatchAction);

    createCreateStartDateFilterAction = sinon.stub().returns(CREATE_FILTER_ACTION);
    __set__('createCreateStartDateFilterAction', createCreateStartDateFilterAction);

    createDeleteFilterAction = sinon.stub().returns(DELETE_FILTER);
    __set__('createDeleteFilterAction', createDeleteFilterAction);
  });

  afterEach(() => {
    __ResetDependency__('dispatchAction');
    __ResetDependency__('createCreateStartDateFilterAction');
    __ResetDependency__('createDeleteFilterAction');
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

  describe('formatDate', () => {
    it('should return only the date portion without time', () => {
      const formatted = formatDate(new Date('2017-02-15T12:00:00'));

      expect(formatted).to.eql('2017-02-15');
    });
  });

  describe('delete filter', () => {
    const filter = {start: 'date', end: 'anotherDate'};

    beforeEach(() => {
      deleteFilter(filter);
    });

    it('should dispatch delete filter action', () => {
      expect(dispatchAction.calledWith(DELETE_FILTER)).to.eql(true);
    });

    it('should create action', () => {
      expect(createDeleteFilterAction.calledOnce).to.eql(true);
      expect(createDeleteFilterAction.calledWith(filter)).to.eql(true);
    });
  });
});
