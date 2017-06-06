import {jsx} from 'view-utils';
import {mountTemplate, triggerEvent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {DateButton, TODAY, YESTERDAY, LAST_MONTH, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/date/DateButton';

describe('<DateButton>', () => {
  let node;
  let update;
  let formatDate;
  let start;
  let end;
  let datepickerFct;
  let $;
  let clock;

  beforeEach(() => {
    clock = sinon.useFakeTimers(new Date('2017-03-30').getTime());

    formatDate = sinon.stub().returnsArg(0);
    __set__('formatDate', formatDate);

    datepickerFct = sinon.spy();
    $ = sinon.stub().returns({
      datepicker: datepickerFct
    });
    __set__('$', $);

    start = {};
    end = {};

    ({node, update} = mountTemplate(<DateButton dateLabel={TODAY} />));
    update({start, end});

    triggerEvent({
      node,
      selector: 'button',
      eventName: 'click'
    });
  });

  afterEach(() => {
    __ResetDependency__('formatDate');
    __ResetDependency__('$');

    clock.restore();
  });

  it('should contain a button', () => {
    expect(node.querySelector('button')).to.exist;
  });

  it('should set the value of start and end date fields', () => {
    expect(datepickerFct.calledWith('setDate')).to.eql(true);
  });

  it('should store the formatted date', () => {
    expect(formatDate.calledTwice).to.eql(true);
  });

  it('should set the date value dependent on the date string', () => {
    const secondStart = {};
    const secondEnd = {};
    const {node: secondNode, update: secondUpdate} = mountTemplate(<DateButton dateLabel={YESTERDAY} />);

    secondUpdate({start: secondStart, end: secondEnd});
    triggerEvent({
      node: secondNode,
      selector: 'button',
      eventName: 'click'
    });

    expect(datepickerFct.args[0][1]).to.not.eql(datepickerFct.args[2][1]);
    expect(datepickerFct.args[1][1]).to.not.eql(datepickerFct.args[3][1]);
  });

  it('should correctly set the last month and not overflow', () => {
    ({node, update} = mountTemplate(<DateButton dateLabel={LAST_MONTH} />));
    update({start, end});

    triggerEvent({
      node,
      selector: 'button',
      eventName: 'click'
    });

    const appliedStart = datepickerFct.args[2][1].toISOString().substr(0, 10);
    const appliedEnd = datepickerFct.args[3][1].toISOString().substr(0, 10);

    expect(appliedStart).to.eql('2017-02-01');
    expect(appliedEnd).to.eql('2017-02-28');
  });
});
