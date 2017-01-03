import {jsx} from 'view-utils';
import {mountTemplate, triggerEvent} from 'testHelpers';
import sinon from 'sinon';
import {expect} from 'chai';
import {DateFilter} from 'main/processDisplay/filters/dateFilter.component';

describe('<DateFilter>', () => {
  const selector = 'date-field';
  let onDateChanged;
  let node;
  let update;
  let input;

  beforeEach(() => {
    onDateChanged = sinon.spy();
    ({node, update} = mountTemplate(<DateFilter selector={selector} onDateChanged={onDateChanged} />));
    input = node.querySelector('input');
  });

  it('should display input field', () => {
    expect(input).to.exist;
  });

  it('should format set date value from state', () => {
    const state = {
      [selector]: new Date(2017, 2, 24)
    };

    update(state);

    expect(input.value).to.eql('2017-3-24');
  });

  it('should call onDateChanged when input has correct date inserted', () => {
    const year = 2017;
    const month = 3;
    const day = 24;
    const dateStr = `${year}-${month}-${day}`;

    input.value = dateStr;
    triggerEvent({
      node: input,
      eventName: 'change'
    });

    expect(onDateChanged.calledOnce).to.eql(true, 'expected onDateChange to be called once');
    expect(onDateChanged.calledWith(new Date(year, month - 1, day))).to.eql(true, `expected date to be ${dateStr}`);
  });

  it('should not call onDateChanged when date has incorrect format', () => {
    input.value = '3022-d';
    triggerEvent({
      node: input,
      eventName: 'change'
    });

    expect(onDateChanged.called).to.eql(false);
  });
});
