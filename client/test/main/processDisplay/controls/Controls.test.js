import {expect} from 'chai';
import sinon from 'sinon';
import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {createControls, __set__, __ResetDependency__} from 'main/processDisplay/controls/Controls';

describe('<Controls>', () => {
  let Controls;
  let Filter;
  let CreateFilter;
  let View;
  let AnalysisSelection;
  let onCriteriaChanged;
  let node;
  let update;

  beforeEach(() => {
    Filter = createMockComponent('Filter');
    CreateFilter = createMockComponent('CreateFilter');
    View = createMockComponent('View');
    AnalysisSelection = createMockComponent('AnalysisSelection');

    __set__('Filter', Filter);
    __set__('CreateFilter', CreateFilter);
    __set__('View', View);
    __set__('AnalysisSelection', AnalysisSelection);

    onCriteriaChanged = sinon.spy();

    Controls = createControls();

    ({node, update} = mountTemplate(<Controls onCriteriaChanged={onCriteriaChanged} />));
  });

  afterEach(() => {
    __ResetDependency__('Filter');
    __ResetDependency__('CreateFilter');
    __ResetDependency__('View');
    __ResetDependency__('AnalysisSelection');
  });

  it('should call the change callback initially', () => {
    update({
      controls: {
        view: 'none'
      },
      display: {
        selection: {}
      }
    });

    expect(onCriteriaChanged.calledOnce).to.eql(true);
  });

  it('should display Filter', () => {
    expect(node).to.contain.text(Filter.text);
  });

  it('should display CreateFilter', () => {
    expect(node).to.contain.text(CreateFilter.text);
  });

  it('should pass onFilterAdded to CreateFilter', () => {
    expect(CreateFilter.getAttribute('onFilterAdded')).to.be.ok;
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
});
