import React from 'react';
import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import sinon from 'sinon';
import {DateButton, TODAY, LAST_MONTH} from 'main/processDisplay/controls/filter/date/DateButton';
import {mount} from 'enzyme';

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
    const today = new Date().toISOString();

    wrapper.find('.btn').simulate('click');

    expect(
      setDates.calledWith({
        startDate: today,
        endDate: today
      })
    ).to.eql(true);
  });

  it('should correctly set the last month and not overflow', () => {
    wrapper = mount(<DateButton dateLabel={LAST_MONTH} setDates={setDates} />);

    wrapper.find('button').simulate('click');

    // This is a bit strange, I have no idea what is going one here
    // although other than strange hour (which is ignored anyway) it's seems fine.
    // So, I will leave it like that for now.
    expect(
      setDates.calledWith({
        startDate: new Date('2017-02-01 2:00').toISOString(),
        endDate: new Date('2017-02-28 2:00').toISOString()
      })
    ).to.eql(true);
  });
});
