import React from 'react';
import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import sinon from 'sinon';
import moment from 'moment';
import {createReactMock} from 'testHelpers';
import {DateFields, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/date/DateFields';
import {mount} from 'enzyme';

chai.use(chaiEnzyme());

const jsx = React.createElement;
const {expect} = chai;

describe('main/processDisplay/controls/filter/date <DateFields>', () => {
  const format = 'YYYY-MM-DD';
  let DateInput;
  let DateRange;
  let $setTimeout;
  let event;
  let startDate;
  let endDate;
  let onDateChange;
  let wrapper;

  beforeEach(() => {
    DateInput = createReactMock('DateInput');
    __set__('DateInput', DateInput);

    DateRange = createReactMock('DateRange');
    __set__('DateRange', DateRange);

    $setTimeout = sinon.stub().callsArg(0);
    __set__('$setTimeout', $setTimeout);

    event = {
      nativeEvent: {
        stopImmediatePropagation: sinon.spy()
      }
    };

    startDate = moment([2017, 8, 29]);
    endDate = moment([2020, 6, 5]);
    onDateChange = sinon.spy();

    wrapper = mount(<DateFields format={format}
                                startDate={startDate}
                                endDate={endDate}
                                onDateChange={onDateChange} />);
  });

  afterEach(() => {
    __ResetDependency__('DateInput');
    __ResetDependency__('DateRange');
    __ResetDependency__('$setTimeout');
  });

  it('should have start date input field', () => {
    expect(
      DateInput.calledWith({
        className: 'form-control start'
      })
    ).to.eql(true);
  });

  it('should have end date input field', () => {
    expect(
      DateInput.calledWith({
        className: 'form-control end'
      })
    ).to.eql(true);
  });

  it('should set startDate on date change of start date input field', () => {
    const onDateInputChange = DateInput.getProperty('onDateChange', 0);

    onDateInputChange('change');

    expect(onDateChange.calledWith('startDate', 'change')).to.eql(true);
  });

  it('should set endDate on date change of start date input field', () => {
    const onDateInputChange = DateInput.getProperty('onDateChange', 1);

    onDateInputChange('date');

    expect(onDateChange.calledWith('endDate', 'date')).to.eql(true);
  });

  it('should select date range popup on date input click', () => {
    const onClick = DateInput.getProperty('onClick', 0);

    onClick(event);

    expect(event.nativeEvent.stopImmediatePropagation.called).to.eql(true);
    expect(wrapper).to.have.state('popupOpen', true);
    expect(wrapper).to.have.state('currentlySelectedField', 'startDate');
  });

  describe('DateRange', () => {
    let onDateRangeChange;

    beforeEach(() => {
      const onClick = DateInput.getProperty('onClick', 0);

      onClick(event);

      onDateRangeChange = DateRange.getProperty('onDateChange');
    });

    it('should have DateRange', () => {
      expect(wrapper.find(DateRange)).to.exist;
    });

    it('should set date on date change in DateRange', () => {
      onDateRangeChange('date1');

      expect(onDateChange.calledWith('startDate', 'date1')).to.eql(true);
    });

    it('should change currently selected date to endDate', () => {
      onDateRangeChange('whatever');

      expect(wrapper).to.have.state('currentlySelectedField', 'endDate');
    });

    it('should selected endDate after second selection', () => {
      onDateRangeChange('whatever');
      onDateRangeChange('date2');

      expect(onDateChange.calledWith('endDate', 'date2')).to.eql(true);
    });
  });
});
