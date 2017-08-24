import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {expect} from 'chai';
import {FilterBar, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/FilterBar';

describe('<FilterBar>', () => {
  let node;
  let update;
  let DateFilter;
  let VariableFilter;
  let ExecutedNodeFilter;

  beforeEach(() => {
    DateFilter = createMockComponent('DateFilter');
    __set__('DateFilter', DateFilter);

    VariableFilter = createMockComponent('VariableFilter');
    __set__('VariableFilter', VariableFilter);

    ExecutedNodeFilter = createMockComponent('ExecutedNodeFilter');
    __set__('ExecutedNodeFilter', ExecutedNodeFilter);

    ({node, update} = mountTemplate(<FilterBar />));
  });

  afterEach(() => {
    __ResetDependency__('DateFilter');
    __ResetDependency__('VariableFilter');
    __ResetDependency__('ExecutedNodeFilter');
  });

  it('should contain a filter list', () => {
    expect(node.querySelector('ul')).to.exist;
  });

  it('should be empty by default', () => {
    update({filter: []});

    expect(node.querySelector('ul').textContent).to.be.empty;
  });

  it('should contain a representation of a startDate filter', () => {
    update({filter: [
      {type: 'startDate'}
    ]});

    expect(node.querySelector('ul').textContent).to.eql('DateFilter');
  });

  it('should contain a representation of a variable filter', () => {
    update({filter: [
      {type: 'variable'}
    ]});

    expect(node.querySelector('ul').textContent).to.eql('VariableFilter');
  });

  it('should contain a representation of a executed node filter', () => {
    update({filter: [
      {type: 'executedNode'}
    ]});

    expect(node.querySelector('ul').textContent).to.eql('ExecutedNodeFilter');
  });
});
