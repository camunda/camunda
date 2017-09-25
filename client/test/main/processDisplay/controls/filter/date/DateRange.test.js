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
      wrapper = mount(<DateRange startDate={startDate} endDate={endDate} onDateChange={onDateChange} />);
    });

    it('should display two Calendars', () => {
      expect(wrapper.find('.Calendar').length).to.eql(2);
    });

    it('should add Calender with range from startDate to endDate', () => {
      const range = Calendar.getProperty('range');

      expect(range.startDate.format(format)).to.eql('2012-12-15');
      expect(range.endDate.format(format)).to.eql('2018-05-02');
    });

    it('should call onDateChange property when date changes', () => {
      const onChange = Calendar.getProperty('onChange');

      onChange('new date');

      expect(onDateChange.calledWith('startDate', 'new date')).to.eql(true);
    });

    describe('changing month', () => {
      let linkCB;

      describe('start date Calendar', () => {
        beforeEach(() => {
          linkCB = Calendar.getProperty('linkCB', 0);
        });

        it('should change start link by one month forward when direction is 1', () => {
          linkCB(1);

          expect(wrapper.state('startLink').format(format)).to.have.eql('2013-01-15');
          expect(wrapper.state('endLink').format(format)).to.have.eql('2018-05-02');
        });

        it('should change start link by one month backward when direction is -1', () => {
          linkCB(-1);

          expect(wrapper.state('startLink').format(format)).to.have.eql('2012-11-15');
          expect(wrapper.state('endLink').format(format)).to.have.eql('2018-05-02');
        });
      });

      describe('end date Calendar', () => {
        beforeEach(() => {
          linkCB = Calendar.getProperty('linkCB', 1);
        });

        it('should change end link by one month forward when direction is 1', () => {
          linkCB(1);

          expect(wrapper.state('endLink').format(format)).to.have.eql('2018-06-02');
          expect(wrapper.state('startLink').format(format)).to.have.eql('2012-12-15');
        });

        it('should change end link by one month backward when direction is -11', () => {
          linkCB(-1);

          expect(wrapper.state('endLink').format(format)).to.have.eql('2018-04-02');
          expect(wrapper.state('startLink').format(format)).to.have.eql('2012-12-15');
        });
      });
    });
  });

  describe('with same dates', () => {
    let startLinkCB;
    let endLinkCB;

    beforeEach(() => {
      wrapper = mount(<DateRange startDate={startDate} endDate={startDate} onDateChange={onDateChange} />);
      startLinkCB = Calendar.getProperty('linkCB', 0);
      endLinkCB = Calendar.getProperty('linkCB', 1);
    });

    it('start link change should move both calendars by 1 if links are the same', () => {
      startLinkCB(1);

      expect(wrapper.state('startLink').format(format)).to.have.eql('2013-01-15');
      expect(wrapper.state('endLink').format(format)).to.have.eql('2013-01-15');
    });

    it('end link change should move both calendars by -1 if links are the same', () => {
      endLinkCB(-1);

      expect(wrapper.state('startLink').format(format)).to.have.eql('2012-11-15');
      expect(wrapper.state('endLink').format(format)).to.have.eql('2012-11-15');
    });
  });
});
