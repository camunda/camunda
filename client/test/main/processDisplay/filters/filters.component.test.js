import {jsx} from 'view-utils';
import {mountTemplate, triggerEvent, selectByText} from 'testHelpers';
import sinon from 'sinon';
import {expect} from 'chai';
import {Filters, __set__, __ResetDependency__} from 'main/processDisplay/filters/filters.component';

describe('<Filters>', () => {
  const changeAction = 'change-action';
  let dispatchAction;
  let createChangeFilterAction;
  let node;
  let startDateFilter;
  let endDateFilter;

  beforeEach(() => {
    dispatchAction = sinon.spy();
    __set__('dispatchAction', dispatchAction);

    createChangeFilterAction = sinon.stub().returns(changeAction);
    __set__('createChangeFilterAction', createChangeFilterAction);

    ({node} = mountTemplate(<Filters/>));

    startDateFilter = selectByText(
      node.querySelectorAll('.filters__filter'),
      'Start Date:'
    )[0];

    endDateFilter = selectByText(
      node.querySelectorAll('.filters__filter'),
      'End Date:'
    )[0];
  });

  afterEach(() => {
    __ResetDependency__('dispatchAction');
    __ResetDependency__('createChangeFilterAction');
  });

  it('should display start and end date filter', () => {
    expect(startDateFilter).to.exist;
    expect(endDateFilter).to.exist;
  });

  it('should dispatch change filter action when start filter changes', () => {
    const input = startDateFilter.querySelector('input');
    const year = 2015;
    const month = 4;
    const day = 28;
    const dateStr = `${year}-${month}-${day}`;
    const expectedDate = new Date(year, month - 1, day);

    input.value = dateStr;
    triggerEvent({
      node: input,
      eventName: 'change'
    });

    expect(createChangeFilterAction.calledWith('startDate', expectedDate)).to.eql(true);
    expect(dispatchAction.calledWith(changeAction)).to.eql(true);
  });

  it('should dispatch change filter action when end filter changes', () => {
    const input = endDateFilter.querySelector('input');
    const year = 2015;
    const month = 4;
    const day = 28;
    const dateStr = `${year}-${month}-${day}`;
    const expectedDate = new Date(year, month - 1, day);

    input.value = dateStr;
    triggerEvent({
      node: input,
      eventName: 'change'
    });

    expect(createChangeFilterAction.calledWith('endDate', expectedDate)).to.eql(true);
    expect(dispatchAction.calledWith(changeAction)).to.eql(true);
  });
});
