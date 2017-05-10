import {expect} from 'chai';
import sinon from 'sinon';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {jsx, DESTROY_EVENT} from 'view-utils';
import {Filter, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/Filter';

describe('<Filter>', () => {
  let CreateFilter;
  let FilterBar;
  let getFilter;
  let onFilterChanged;
  let onHistoryStateChange;
  let removeHistoryListener;
  let getProcessDefinition;
  let node;
  let update;
  let eventsBus;

  beforeEach(() => {
    CreateFilter = createMockComponent('CreateFilter');
    __set__('CreateFilter', CreateFilter);

    FilterBar = createMockComponent('FilterBar');
    __set__('FilterBar', FilterBar);

    getFilter = sinon.stub().returns('filter');
    __set__('getFilter', getFilter);

    removeHistoryListener = sinon.spy();

    getProcessDefinition = sinon.spy();

    onHistoryStateChange = sinon.stub().returns(removeHistoryListener);
    __set__('onHistoryStateChange', onHistoryStateChange);

    onFilterChanged = sinon.spy();

    ({node, update, eventsBus} = mountTemplate(<Filter onFilterChanged={onFilterChanged} getProcessDefinition={getProcessDefinition} />));
  });

  afterEach(() => {
    __ResetDependency__('CreateFilter');
    __ResetDependency__('FilterBar');
    __ResetDependency__('getFilter');
    __ResetDependency__('onHistoryStateChange');
  });

  it('should display FilterBar', () => {
    expect(node).to.contain.text(FilterBar.text);
  });

  it('should display CreateFilter', () => {
    expect(node).to.contain.text(CreateFilter.text);
  });

  it('should add filter to state on update', () => {
    const state = {a: 1};

    update(state);

    expect(FilterBar.mocks.update.calledWith({
      ...state,
      filter: 'filter'
    })).to.eql(true);
  });

  it('should add history listener', () => {
    expect(onHistoryStateChange.calledOnce).to.eql(true);
    expect(onHistoryStateChange.calledWith(onFilterChanged)).to.eql(true);
  });

  it('should pass getDefinitionId function to CreateFilter component', () => {
    expect(CreateFilter.getAttribute('getProcessDefinition')).to.eql(getProcessDefinition);
  });

  it('should remove history listener on destroy event', () => {
    eventsBus.fireEvent(DESTROY_EVENT, {});

    expect(removeHistoryListener.calledOnce).to.eql(true);
  });
});
