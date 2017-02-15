import {jsx} from 'view-utils';
import {mountTemplate, triggerEvent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {DateButton, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/DateButton';

describe('<DateButton>', () => {
  let node;
  let update;
  let formatDate;
  let start;
  let end;

  beforeEach(() => {
    formatDate = sinon.stub().returnsArg(0);
    __set__('formatDate', formatDate);

    start = {};
    end = {};

    ({node, update} = mountTemplate(<DateButton date="Today" />));
    update({start, end});

    triggerEvent({
      node,
      selector: 'button',
      eventName: 'click'
    });
  });

  afterEach(() => {
    __ResetDependency__('formatDate');
  });

  it('should contain a button', () => {
    expect(node.querySelector('button')).to.exist;
  });

  it('should set the value of start and end date fields', () => {
    expect(start.value).to.exist;
    expect(end.value).to.exist;
  });

  it('should store the formatted date', () => {
    expect(formatDate.calledTwice).to.eql(true);
  });

  it('should set the date value dependent on the date string', () => {
    const secondStart = {};
    const secondEnd = {};
    const {node: secondNode, update: secondUpdate} = mountTemplate(<DateButton date="Yesterday" />);

    secondUpdate({start: secondStart, end: secondEnd});
    triggerEvent({
      node: secondNode,
      selector: 'button',
      eventName: 'click'
    });

    expect(start.value).to.not.eql(secondStart.value);
    expect(end.value).to.not.eql(secondEnd.value);
  });
});
