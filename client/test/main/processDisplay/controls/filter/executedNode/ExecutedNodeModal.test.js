import {jsx} from 'view-utils';
import {mountTemplate, triggerEvent, createMockComponent, selectByText} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {createExecutedNodeModal, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/executedNode/ExecutedNodeModal';

describe('main/processDisplay/controls/filter/executedNode <ExecutedNodeModal>', () => {
  let Modal;
  let SelectNodeDiagram;
  let Socket;
  let ExecutedNodeModal;
  let changeSelectedNodes;
  let onFilterAdded;
  let getDiagramXML;
  let onNextTick;
  let update;
  let filterType;

  beforeEach(() => {
    Modal = createMockComponent('Modal', true);
    Modal.close = sinon.spy();
    Modal.open = sinon.spy();
    __set__('createModal', sinon.stub().returns(Modal));

    SelectNodeDiagram = createMockComponent('SelectNodeDiagram');
    SelectNodeDiagram.loadDiagram = sinon.spy();
    __set__('createSelectedNodeDiagram', sinon.stub().returns(SelectNodeDiagram));

    Socket = createMockComponent('Socket', true);
    __set__('Socket', Socket);

    changeSelectedNodes = sinon.spy();
    __set__('changeSelectedNodes', changeSelectedNodes);

    onFilterAdded = sinon.spy();
    getDiagramXML = sinon.stub().returns('diagram');

    onNextTick = sinon.stub().callsArg(0);
    __set__('onNextTick', onNextTick);

    filterType = 'filter-type';
    __set__('filterType', filterType);

    ExecutedNodeModal = createExecutedNodeModal(onFilterAdded, getDiagramXML);

    ({update} = mountTemplate(<ExecutedNodeModal />));
  });

  afterEach(() => {
    __ResetDependency__('createModal');
    __ResetDependency__('createSelectedNodeDiagram');
    __ResetDependency__('Socket');
    __ResetDependency__('changeSelectedNodes');
    __ResetDependency__('onNextTick');
    __ResetDependency__('filterType');
  });

  it('should loadDiagram on Modal open', () => {
    const open = Modal.getAttribute('onOpen');

    open();

    expect(SelectNodeDiagram.loadDiagram.calledWith('diagram', [])).to.eql(true);
    expect(getDiagramXML.calledOnce).to.eql(true);
  });

  describe('modal head', () => {
    let head;

    beforeEach(() => {
      head = Socket.getChildrenNode({name: 'head'});
    });

    it('should add close button', () => {
      expect(head).to.contain('button.close');
    });

    it('should call Modal.close on close button clicked', () => {
      triggerEvent({
        node: head.querySelector('button.close'),
        eventName: 'click'
      });
    });
  });

  describe('modal body', () => {
    let body;

    beforeEach(() => {
      body = Socket.getChildrenNode({name: 'body'});
    });

    it('should contain selected node diagram', () => {
      expect(body).to.contain.text(SelectNodeDiagram.text);
    });

    it('should pass onSelectionChange callback to SelectNodeDiagram that changes currently selected nodes passed to diagram', () => {
      const onSelectionChange = SelectNodeDiagram.getAttribute('onSelectionChange');
      const nodes = 'nodes';

      onSelectionChange(nodes);

      update([]);

      expect(SelectNodeDiagram.updatedWith(nodes)).to.eql(true);
    });
  });

  describe('modal foot', () => {
    let foot;

    beforeEach(() => {
      foot = Socket.getChildrenNode({name: 'foot'});
    });

    it('should display Abort and Create Filter buttons', () => {
      expect(foot).to.contain.text('Abort');
      expect(foot).to.contain.text('Create Filter');
    });

    it('should close Modal on abort', () => {
      const [abortBtn] = selectByText(foot.querySelectorAll('button'), 'Abort');

      triggerEvent({
        node: abortBtn,
        eventName: 'click'
      });

      expect(Modal.close.calledOnce).to.eql(true);
    });

    it('should close Modal and change selected nodes on Create Filter', () => {
      const [createBtn] = selectByText(foot.querySelectorAll('button'), 'Create Filter');
      const onSelectionChange = SelectNodeDiagram.getAttribute('onSelectionChange');
      const nodes = 'nodes' + Math.random();

      onSelectionChange(nodes);

      triggerEvent({
        node: createBtn,
        eventName: 'click'
      });

      expect(changeSelectedNodes.calledWith(nodes))
        .to.eql(true, 'expected selected nodes to be changed');
      expect(Modal.close.calledOnce)
        .to.eql(true, 'expected Modal to be closed');
      expect(onNextTick.calledWith(onFilterAdded))
        .to.eql(true, 'expected onFilterAdded to be executed on next tick');
      expect(onFilterAdded.called)
        .to.eql(true, 'expected onFilterAdded to be called');
    });
  });

  describe('open', () => {
    it('should extract currently selected nodes from state', () => {
      const nodes = 'nodes-34';
      const state = {
        filter: [
          {
            type: 'other',
            data: 'dd1'
          },
          {
            type: filterType,
            data: nodes
          }
        ]
      };

      update(state);

      ExecutedNodeModal.open();

      expect(Modal.open.calledOnce).to.eql(true, 'expected Modal to be opened');
    });
  });
});
