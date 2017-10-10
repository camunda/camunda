import React from 'react';
import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import sinon from 'sinon';
import moment from 'moment';
import {createReactMock} from 'testHelpers';
import {DateRange, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/date/DateRange';
import {mount} from 'enzyme';

chai.use(chaiEnzyme());

const jsx = React.createElement;
const {expect} = chai;

describe('main/processDisplay/controls/filter/date <DateRange>', () => {
  const format = 'YYYY-MM-DD';
  const minDate = 'min-date';
  const maxDate = 'max-date';
  let startDate;
  let endDate;
  let onDateChange;
  let Calendar;
  let wrapper;

  beforeEach(() => {
    startDate = moment('2012-12-15', format);
    endDate = moment('2018-05-02', format);
    onDateChange = sinon.spy();

    Calendar = createReactMock('Calendar');
    __set__('Calendar', Calendar);
  });

  afterEach(() => {
    __ResetDependency__('Calendar');
  });

  describe('with different dates', () => {
    beforeEach(() => {
      wrapper = mount(
        <DateRange
          minDate={minDate}
          maxDate={maxDate}
          startDate={startDate}
          endDate={endDate}
          onDateChange={onDateChange} />
      );
    });

    it('should display two Calendars', () => {
      expect(wrapper.find(Calendar).length).to.eql(2);
    });

    it('should add Calender with range from startDate to endDate', () => {
      const range = Calendar.getProperty('range');

      expect(range.startDate.format(format)).to.eql('2012-12-15');
      expect(range.endDate.format(format)).to.eql('2018-05-02');
    });

    it('should call onDateChange property when date changes', () => {
      const onChange = Calendar.getProperty('onChange');
      const newDate = 'new date';

      onChange(newDate);

      expect(onDateChange.calledWith(newDate)).to.eql(true);
    });

    it('should not disable inner arrows', () => {
      expect(wrapper).to.have.state('innerArrowsDisabled', false);
    });

    it('should pass max and min dates properties to Calendars', () => {
      expect(Calendar.calledWith({minDate, maxDate})).to.eql(true);
    });

    describe('changing month', () => {
      let linkCB;

      describe('start date Calendar', () => {
        beforeEach(() => {
          linkCB = Calendar.getProperty('linkCB', 0);
        });

        it('should change start link by one month forward when direction is 1', () => {
          linkCB(1);

          expect(wrapper.state('startLink').format(format)).to.have.eql('2013-01-01');
          expect(wrapper.state('endLink').format(format)).to.have.eql('2018-05-01');
        });

        it('should change start link by one month backward when direction is -1', () => {
          linkCB(-1);

          expect(wrapper.state('startLink').format(format)).to.have.eql('2012-11-01');
          expect(wrapper.state('endLink').format(format)).to.have.eql('2018-05-01');
        });
      });

      describe('end date Calendar', () => {
        beforeEach(() => {
          linkCB = Calendar.getProperty('linkCB', 1);
        });

        it('should change end link by one month forward when direction is 1', () => {
          linkCB(1);

          expect(wrapper.state('endLink').format(format)).to.have.eql('2018-06-01');
          expect(wrapper.state('startLink').format(format)).to.have.eql('2012-12-01');
        });

        it('should change end link by one month backward when direction is -11', () => {
          linkCB(-1);

          expect(wrapper.state('endLink').format(format)).to.have.eql('2018-04-01');
          expect(wrapper.state('startLink').format(format)).to.have.eql('2012-12-01');
        });
      });
    });
  });

  describe('with same dates', () => {
    beforeEach(() => {
      wrapper = mount(<DateRange startDate={startDate} endDate={startDate} onDateChange={onDateChange} />);
    });

    it('should disable inner arrows', () => {
      expect(wrapper).to.have.state('innerArrowsDisabled', true);
    });
  });
});
