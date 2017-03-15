import {jsx} from 'view-utils';
import {mountTemplate, triggerEvent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {DateButton, TODAY, YESTERDAY, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/DateButton';

describe('<DateButton>', () => {
  let node;
  let update;
  let formatDate;
  let start;
  let end;
  let datepickerFct;
  let $;

  beforeEach(() => {
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
});
