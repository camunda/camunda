import {expect} from 'chai';
import sinon from 'sinon';
import {
  createStartDateFilter, formatDate,
  __set__, __ResetDependency__
} from 'main/processDisplay/controls/filter/date/service';

describe('Date Filter service', () => {
  const CREATE_FILTER_ACTION = 'CREATE_FILTER_ACTION';

  let dispatch;
  let createCreateStartDateFilterAction;

  beforeEach(() => {
    dispatch = sinon.spy();
    __set__('dispatch', dispatch);

    createCreateStartDateFilterAction = sinon.stub().returns(CREATE_FILTER_ACTION);
    __set__('createCreateStartDateFilterAction', createCreateStartDateFilterAction);
  });

  afterEach(() => {
    __ResetDependency__('dispatchAction');
    __ResetDependency__('createCreateStartDateFilterAction');
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

    it('should format date with time at start of the day', () => {
      const formatted = formatDate(new Date('2017-02-15T12:00:00'), {
        withTime: true
      });

      expect(formatted).to.eql('2017-02-15T00:00:00');
    });

    it('should format date with time at end of the day', () => {
      const formatted = formatDate(new Date('2017-02-15T12:00:00'), {
        withTime: true,
        endOfDay: true
      });

      expect(formatted).to.eql('2017-02-15T23:59:59');
    });
  });
});
