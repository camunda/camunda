import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {CreateFilter, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/CreateFilter';

describe('<CreateFilter>', () => {
  let node;
  let Dropdown;
  let DateModal;
  let VariableModal;
  let ExecutedNodeModal;
  let createDateModal;
  let createVariableModal;
  let createExecutedNodeModal;
  let Socket;
  let DropdownItem;
  let getProcessDefinition;
  let onFilterAdded;

  beforeEach(() => {
    Dropdown = createMockComponent('Dropdown', true);
    __set__('Dropdown', Dropdown);

    DateModal = createMockComponent('DateModal', true);
    DateModal.open = sinon.spy();
    createDateModal = sinon.stub().returns(DateModal);
    __set__('createDateModal', createDateModal);

    VariableModal = createMockComponent('VariableModal', true);
    VariableModal.open = sinon.spy();
    createVariableModal = sinon.stub().returns(VariableModal);
    __set__('createVariableModal', createVariableModal);

    ExecutedNodeModal = createMockComponent('ExecutedNodeModal', true);
    ExecutedNodeModal.open = sinon.spy();
    createExecutedNodeModal = sinon.stub().returns(ExecutedNodeModal);
    __set__('createExecutedNodeModal', createExecutedNodeModal);

    Socket = createMockComponent('Socket', true);
    __set__('Socket', Socket);

    DropdownItem = createMockComponent('DropdownItem', true);
    __set__('DropdownItem', DropdownItem);

    onFilterAdded = sinon.spy();

    getProcessDefinition = sinon.spy();

    ({node} = mountTemplate(<CreateFilter onFilterAdded={onFilterAdded} getProcessDefinition={getProcessDefinition} />));
  });

  afterEach(() => {
    __ResetDependency__('Dropdown');
    __ResetDependency__('createDateModal');
    __ResetDependency__('createVariableModal');
    __ResetDependency__('createExecutedNodeModal');
    __ResetDependency__('Socket');
    __ResetDependency__('DropdownItem');
  });

  describe('Dropdown', () => {
    it('should be included', () => {
      expect(node.textContent).to.contain(Dropdown.text);
    });

    it('should contain label socket', () => {
      const labelNode = Socket.getChildrenNode({name: 'label'});

      expect(
        Dropdown.getChildTemplate({attributes: {name: 'label'}, text: 'Socket'})
      ).to.exist;
      expect(labelNode).to.contain.text('+');
    });

    it('should contain list socket', () => {
      const listNode = Socket.getChildrenNode({name: 'list'});

      expect(
        Dropdown.getChildTemplate({attributes: {name: 'list'}, text: 'Socket'})
      ).to.exist;
      expect(listNode).to.contain.text('Start Date');
    });

    it('should contain DropdownItem that opens modal', () => {
      expect(DropdownItem.getAttribute('listener')).to.equal(DateModal.open);
    });
  });

  it('should include the Date Modal', () => {
    expect(node.textContent).to.contain(DateModal.text);
  });

  it('should include the Variabe Modal', () => {
    expect(node.textContent).to.contain(VariableModal.text);
  });

  it('should include Executed Node Modal', () => {
    expect(node.textContent).to.contain(ExecutedNodeModal.text);
  });
});
