import {expect} from 'chai';
import sinon from 'sinon';
import moment from 'moment';
import {
  createStartDateFilter, formatDate, sortDates,
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
  });

  describe('sortDates', () => {
    it('should not change order of start and end dates if those are in correct order', () => {
      const {start, end} = sortDates({
        start: moment([2015, 3, 12]), // just reminder 0 is January
        end: moment([2017, 8, 9])
      });

      expect(start.format('YYYY-MM-DD')).to.eql('2015-04-12');
      expect(end.format('YYYY-MM-DD')).to.eql('2017-09-09');
    });

    it('should change order of start and end dates if those are not in correct order', () => {
      const {start, end} = sortDates({
        start: moment([2017, 8, 9]),
        end: moment([2015, 3, 12])
      });

      expect(start.format('YYYY-MM-DD')).to.eql('2015-04-12');
      expect(end.format('YYYY-MM-DD')).to.eql('2017-09-09');
    });
  });
});
