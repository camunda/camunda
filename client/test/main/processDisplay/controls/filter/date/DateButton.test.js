import React from 'react';
import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import sinon from 'sinon';
import {DateButton, TODAY, LAST_MONTH} from 'main/processDisplay/controls/filter/date/DateButton';
import {mount} from 'enzyme';
import moment from 'moment';

chai.use(chaiEnzyme());

const jsx = React.createElement;
const {expect} = chai;

describe('<DateButton>', () => {
  let setDates;
  let wrapper;
  let clock;

  beforeEach(() => {
    setDates = sinon.spy();
    clock = sinon.useFakeTimers(new Date('2017-03-30').getTime());

    wrapper = mount(<DateButton dateLabel={TODAY} setDates={setDates} />);

    wrapper.find('button').simulate('click');
  });

  afterEach(() => {
    clock.restore();
  });

  it('should contain a button', () => {
    expect(wrapper.find('button')).to.be.present();
  });

  it('should set label on element', () => {
    expect(wrapper).to.contain.text(TODAY);
  });

  it('should set dates on click', () => {
    const today = moment();

    wrapper.find('.btn').simulate('click');

    const [{startDate, endDate}] = setDates.lastCall.args;

    expect(startDate.format('YYYY-MM-DD')).to.eql(today.format('YYYY-MM-DD'));
    expect(endDate.format('YYYY-MM-DD')).to.eql(today.format('YYYY-MM-DD'));
  });

  it('should correctly set the last month and not overflow', () => {
    wrapper = mount(<DateButton dateLabel={LAST_MONTH} setDates={setDates} />);

    wrapper.find('button').simulate('click');

    const [{startDate, endDate}] = setDates.lastCall.args;

    expect(startDate.format('YYYY-MM-DD')).to.eql('2017-02-01');
    expect(endDate.format('YYYY-MM-DD')).to.eql('2017-02-28');
  });
});
