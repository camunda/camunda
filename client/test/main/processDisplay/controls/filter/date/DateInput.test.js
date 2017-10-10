import React from 'react';
import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import sinon from 'sinon';
import moment from 'moment';
import {DateInput, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/date/DateInput';
import {mount} from 'enzyme';

chai.use(chaiEnzyme());

const jsx = React.createElement;
const {expect} = chai;

describe('main/processDisplay/controls/filter/date <DateInput>', () => {
  const format = 'YYYY-MM-DD';
  let date;
  let onDateChange;
  let onNextTick;
  let wrapper;

  beforeEach(() => {
    date = moment('2015-03-25', format);
    onDateChange = sinon.spy();

    onNextTick = sinon.stub().callsArg(0);
    __set__('onNextTick', onNextTick);

    wrapper = mount(<DateInput date={date} onDateChange={onDateChange} format={format} />);
  });

  afterEach(() => {
    __ResetDependency__('onNextTick');
  });

  it('should create text input field', () => {
    expect(wrapper.find('input')).to.exist;
  });

  it('should have field with value equal to formated date', () => {
    expect(wrapper.find('input')).to.have.value(date.format(format));
  });

  it('should trigger onDateChange callback when input changes to valid date', () => {
    wrapper.simulate('change', {
      target: {
        value: '2016-05-07'
      }
    });

    expect(onDateChange.called).to.eql(true);
    expect(onDateChange.lastCall.args[0].format(format)).to.eql('2016-05-07');
  });

  it('should add error class to true when input changes to invalid date', () => {
    wrapper.simulate('change', {
      target: {
        value: '2016-07-3'
      }
    });

    expect(wrapper.find('input')).to.have.className('error');
  });
});
