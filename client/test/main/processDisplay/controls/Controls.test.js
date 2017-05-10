import {expect} from 'chai';
import sinon from 'sinon';
import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {createControls, __set__, __ResetDependency__} from 'main/processDisplay/controls/Controls';

describe('<Controls>', () => {
  let Controls;
  let Filter;
  let View;
  let AnalysisSelection;
  let onCriteriaChanged;
  let getProcessDefinition;
  let node;
  let update;
  let getView;
  let getFilter;

  beforeEach(() => {
    Filter = createMockComponent('Filter');
    View = createMockComponent('View');
    AnalysisSelection = createMockComponent('AnalysisSelection');

    __set__('Filter', Filter);
    __set__('View', View);
    __set__('AnalysisSelection', AnalysisSelection);

    getView = sinon.stub().returns('view');
    __set__('getView', getView);

    getFilter = sinon.stub().returns('filter');
    __set__('getFilter', getFilter);

    onCriteriaChanged = sinon.spy();
    getProcessDefinition = sinon.spy();

    Controls = createControls();

    ({node, update} = mountTemplate(<Controls onCriteriaChanged={onCriteriaChanged} getProcessDefinition={getProcessDefinition} />));
  });

  afterEach(() => {
    __ResetDependency__('Filter');
    __ResetDependency__('View');
    __ResetDependency__('AnalysisSelection');
    __ResetDependency__('getView');
    __ResetDependency__('getFilter');
  });

  it('should call the change callback initially', () => {
    update({});

    expect(onCriteriaChanged.calledOnce).to.eql(true);
  });

  it('should display Filter', () => {
    expect(node).to.contain.text(Filter.text);
  });

  it('should display View', () => {
    expect(node).to.contain.text(View.text);
  });

  it('should not display Analysis selection by default', () => {
    expect(node).to.not.contain.text(AnalysisSelection.text);
  });

  describe('Branch Analysis View', () => {
    let state;

    beforeEach(() => {
      state = {
        controls: {
          view: 'branch_analysis'
        },
        display: {
          selection: {}
        }
      };
      update(state);
    });

    it('should display the analysis selection', () => {
      expect(node).to.not.contain.text(AnalysisSelection.text);
    });
  });

  it('should pass getDefinitionId function to Filter component', () => {
    expect(Filter.getAttribute('getProcessDefinition')).to.eql(getProcessDefinition);
  });

  describe('onViewChanged', () => {
    let onViewChanged;

    beforeEach(() => {
      onViewChanged = View.getAttribute('onViewChanged');
    });

    it('should create filter object and call onCriteriaChanged with it', () => {
      onViewChanged('view');

      expect(onCriteriaChanged.called).to.eql(true, 'expected onCriteriaChanged to be called');
      expect(
        onCriteriaChanged.calledWith({
          query: 'filter',
          view: 'view'
        })
      ).to.eql(true, 'expected onCriteriaChanged to be called with right filter');
    });
  });
});
