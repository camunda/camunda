import {expect} from 'chai';
import sinon from 'sinon';
import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {Controls, __set__, __ResetDependency__} from 'main/processDisplay/controls/Controls';

describe('<Controls>', () => {
  let Filter;
  let CreateFilter;
  let ProcessDefinition;
  let getDefinitionId;
  let Result;
  let View;
  let onCriteriaChanged;
  let node;
  let update;

  beforeEach(() => {
    Filter = createMockComponent('Filter');
    CreateFilter = createMockComponent('CreateFilter');
    ProcessDefinition = createMockComponent('ProcessDefinition');
    Result = createMockComponent('Result');
    View = createMockComponent('View');
    getDefinitionId = sinon.stub().returnsArg(0);

    __set__('Filter', Filter);
    __set__('CreateFilter', CreateFilter);
    __set__('ProcessDefinition', ProcessDefinition);
    __set__('Result', Result);
    __set__('View', View);
    __set__('getDefinitionId', getDefinitionId);

    onCriteriaChanged = sinon.spy();

    ({node, update} = mountTemplate(<Controls selector="controls" onCriteriaChanged={onCriteriaChanged} />));
  });

  afterEach(() => {
    __ResetDependency__('Filter');
    __ResetDependency__('CreateFilter');
    __ResetDependency__('ProcessDefinition');
    __ResetDependency__('Result');
    __ResetDependency__('View');
    __ResetDependency__('getDefinitionId');
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

  it('should display ProcessDefinition', () => {
    expect(node).to.contain.text(ProcessDefinition.text);
  });

  it('should pass selector to ProcessDefinition', () => {
    expect(ProcessDefinition.getAttribute('selector')).to.eql('processDefinition');
  });

  it('should pass onProcessDefinitionSelected to ProcessDefinition', () => {
    expect(ProcessDefinition.getAttribute('onProcessDefinitionSelected')).to.be.ok;
  });

  it('should display Result', () => {
    expect(node).to.contain.text(Result.text);
  });

  it('should display View', () => {
    expect(node).to.contain.text(View.text);
  });

  describe('onProcessDefinitionSelected', () => {
    let onProcessDefinitionSelected;
    let state;

    beforeEach(() => {
      onProcessDefinitionSelected = ProcessDefinition.getAttribute('onProcessDefinitionSelected');
      state = {
        controls: {
          processDefinition: 'definition',
          filter: []
        }
      };
      update(state);
    });

    it('should create filter object and call onCriteriaChanged with it', () => {
      onProcessDefinitionSelected();

      expect(onCriteriaChanged.called).to.eql(true, 'expected onFilterChanged to be called');
      expect(
        onCriteriaChanged.calledWith({
          definition: state.controls.processDefinition,
          query: []
        })
      ).to.eql(true, 'expected onFilterChanged to be called with right filter');
    });
  });
});
