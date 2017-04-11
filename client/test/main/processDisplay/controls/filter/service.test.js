import {expect} from 'chai';
import sinon from 'sinon';
import {createStartDateFilter, formatDate, deleteFilter, getFilter,
        __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/service';

describe('Filter service', () => {
  const CREATE_FILTER_ACTION = 'CREATE_FILTER_ACTION';
  const DELETE_FILTER = 'DELETE_FILTER';

  let dispatch;
  let createCreateStartDateFilterAction;
  let createDeleteFilterAction;
  let parse;
  let getLastRoute;

  beforeEach(() => {
    dispatch = sinon.spy();
    __set__('dispatch', dispatch);

    createCreateStartDateFilterAction = sinon.stub().returns(CREATE_FILTER_ACTION);
    __set__('createCreateStartDateFilterAction', createCreateStartDateFilterAction);

    createDeleteFilterAction = sinon.stub().returns(DELETE_FILTER);
    __set__('createDeleteFilterAction', createDeleteFilterAction);

    parse = sinon.stub().returns('parsed');
    __set__('parse', parse);

    getLastRoute = sinon.stub().returns({
      params: 'params'
    });
    __set__('getLastRoute', getLastRoute);
  });

  afterEach(() => {
    __ResetDependency__('dispatchAction');
    __ResetDependency__('createCreateStartDateFilterAction');
    __ResetDependency__('createDeleteFilterAction');
    __ResetDependency__('parse');
    __ResetDependency__('getLastRoute');
  });

  describe('createStartDateFilter', () => {
    const start = '2016-12-01T00:00:00';
    const end = '2016-12-31T23:59:59';

    beforeEach(() => {
      createStartDateFilter(start, end);
    });
    it('should dispatch open modal action', () => {
      expect(dispatch.calledWith(CREATE_FILTER_ACTION)).to.eql(true);
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

  describe('deleteFilter', () => {
    const filter = {start: 'date', end: 'anotherDate'};

    beforeEach(() => {
      deleteFilter(filter);
    });

    it('should dispatch delete filter action', () => {
      expect(dispatch.calledWith(DELETE_FILTER)).to.eql(true);
    });

    it('should create action', () => {
      expect(createDeleteFilterAction.calledOnce).to.eql(true);
      expect(createDeleteFilterAction.calledWith(filter)).to.eql(true);
    });
  });

  describe('getFilter', () => {
    it('should return parsed last route params', () => {
      expect(getFilter()).to.eql('parsed');
      expect(getLastRoute.calledOnce)
        .to.eql(true, 'expected last route to be fetched');
      expect(parse.calledWith('params'))
        .to.eql(true, 'expected params to be parsed');
    });
  });
});
