import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent, triggerEvent, selectByText} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {createVariableModal, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/variable/VariableModal';

describe('<VariableModal>', () => {
  const processDefinition = 'some process definition';

  let node;
  let update;
  let Modal;
  let createModal;
  let Socket;
  let onNextTick;
  let createCallback;
  let isInitial;
  let loadVariables;
  let selectVariableIdx;
  let deselectVariableIdx;
  let createVariableFilter;
  let OperatorSelection;
  let ValueInput;
  let ValueSelection;
  let getProcessDefinition;
  let VariableModal;
  let variables;

  beforeEach(() => {
    variables = {
      data: [
        {type: 'Boolean', name: 'b'},
        {type: 'String', name: 's'},
        {type: 'Double', name: 'd'}
      ]
    };

    isInitial = sinon.stub().returns(false);
    __set__('isInitial', isInitial);

    Modal = createMockComponent('Modal', true);
    Modal.open = sinon.spy();
    Modal.close = sinon.spy();
    createModal = sinon.stub().returns(Modal);
    __set__('createModal', createModal);

    Socket = createMockComponent('Socket', true);
    __set__('Socket', Socket);

    OperatorSelection = createMockComponent('OperatorSelection');
    __set__('OperatorSelection', OperatorSelection);

    ValueInput = createMockComponent('ValueInput');
    __set__('ValueInput', ValueInput);

    ValueSelection = createMockComponent('ValueSelection');
    __set__('ValueSelection', ValueSelection);

    loadVariables = sinon.spy();
    __set__('loadVariables', loadVariables);

    selectVariableIdx = sinon.spy();
    __set__('selectVariableIdx', selectVariableIdx);

    deselectVariableIdx = sinon.spy();
    __set__('deselectVariableIdx', deselectVariableIdx);

    createVariableFilter = sinon.spy();
    __set__('createVariableFilter', createVariableFilter);

    onNextTick = sinon.stub().callsArg(0);
    __set__('onNextTick', onNextTick);

    createCallback = sinon.spy();
    getProcessDefinition = sinon.stub().returns(processDefinition);

    VariableModal = createVariableModal(createCallback, getProcessDefinition);

    ({node, update} = mountTemplate(<VariableModal />));
  });

  afterEach(() => {
    __ResetDependency__('isInitial');
    __ResetDependency__('createModal');
    __ResetDependency__('Socket');
    __ResetDependency__('OperatorSelection');
    __ResetDependency__('onNextTick');
    __ResetDependency__('ValueInput');
    __ResetDependency__('ValueSelection');
    __ResetDependency__('loadVariables');
    __ResetDependency__('selectVariableIdx');
    __ResetDependency__('deselectVariableIdx');
    __ResetDependency__('createVariableFilter');
  });

  it('should load the variables initially', () => {
    isInitial.returns(true);
    update({variables});

    expect(loadVariables.calledWith(processDefinition)).to.eql(true);
  });

  it('should load variables when the process definition is changed', () => {
    const newDefinition = 'some changed value';

    update({variables});
    getProcessDefinition.returns(newDefinition);
    update({variables});

    expect(loadVariables.calledWith(newDefinition)).to.eql(true);
  });

  it('should reset the selected variable when the modal is opened', () => {
    VariableModal.open();

    expect(deselectVariableIdx.calledOnce).to.eql(true);
  });

  describe('Modal', () => {
    it('should be included', () => {
      expect(node.textContent).to.contain(Modal.text);
    });

    describe('head socket', () => {
      let headNode;

      beforeEach(() => {
        headNode = Socket.getChildrenNode({name: 'head'});
      });

      it('should be included', () => {
        expect(
          Modal.getChildTemplate({attributes: {name: 'head'}, text: 'Socket'})
        ).to.exist;
      });

      it('should contain button that closes modal', () => {
        const button = headNode.querySelector('button.close');

        expect(button).to.exist;

        triggerEvent({
          node: button,
          eventName: 'click'
        });

        expect(Modal.close.calledOnce).to.eql(true);
      });

      it('should contain title', () => {
        expect(headNode.querySelector('h4.modal-title')).to.exist;
      });
    });

    describe('body socket', () => {
      let bodyNode;

      beforeEach(() => {
        bodyNode = Socket.getChildrenNode({name: 'body'});

        update({variables});
      });

      it('should be included', () => {
        expect(
          Modal.getChildTemplate({attributes: {name: 'body'}, text: 'Socket'})
        ).to.exist;
      });

      it('should contain a variable name select input', () => {
        expect(bodyNode.querySelector('select')).to.exist;
      });

      it('should show the variable type if the name is not unique', () => {
        variables.data[0].name = 'a';
        variables.data[1].name = 'a';
        update({variables});

        expect(bodyNode.querySelectorAll('option')[1].textContent).to.include('Boolean');
        expect(bodyNode.querySelectorAll('option')[2].textContent).to.include('String');
      });

      it('should select the variable when the user changes the selection', () => {
        bodyNode.querySelector('select').selectedIndex = 2;

        triggerEvent({
          node: bodyNode,
          selector: 'select',
          eventName: 'change'
        });

        expect(selectVariableIdx.calledWith(1)).to.eql(true);
      });

      it('should create a filter when the form is submitted', () => {
        update({variables, selectedIdx: 0, operator: 1, value: 2});

        triggerEvent({
          node: bodyNode,
          selector: 'form',
          eventName: 'submit'
        });

        expect(createVariableFilter.calledOnce).to.eql(true, 'expected variable filter to be created');
        expect(createCallback.calledOnce).to.eql(true, 'expected onFilterAdded callback to be called');
      });

      it('should contain an operator selection component', () => {
        expect(bodyNode.textContent).to.contain(OperatorSelection.text);
      });

      it('should contain an value input component', () => {
        expect(bodyNode.textContent).to.contain(ValueInput.text);
      });

      it('should contain a value selection component if list of values is complete', () => {
        variables.data = variables.data.map(variable => {
          return {
            ...variable,
            values: [1, 2, 3],
            valuesAreComplete: true
          };
        });
        update({variables, selectedIdx: 1});

        expect(bodyNode.textContent).to.contain(ValueSelection.text);
      });
    });

    describe('foot socket', () => {
      let footNode;

      beforeEach(() => {
        footNode = Socket.getChildrenNode({name: 'foot'});
      });

      it('should be included', () => {
        expect(
          Modal.getChildTemplate({attributes: {name: 'foot'}, text: 'Socket'})
        ).to.exist;
      });

      it('should have close button that closes modal', () => {
        const closeBtn = selectByText(
          footNode.querySelectorAll('button'),
          'Abort'
        )[0];

        triggerEvent({
          node: closeBtn,
          eventName: 'click'
        });

        expect(closeBtn).to.exist;
        expect(Modal.close.calledOnce).to.eql(true, 'expected modal to be closed');
      });

      it('should have create filter button that closes modal', () => {
        update({variables, selectedIdx: 0, operator: 1, value: 2});

        const createBtn = selectByText(
          footNode.querySelectorAll('button'),
          'Create Filter'
        )[0];

        triggerEvent({
          node: createBtn,
          eventName: 'click'
        });

        expect(createBtn).to.exist;
        expect(Modal.close.calledOnce).to.eql(true, 'expected modal to be closed');
        expect(createVariableFilter.calledOnce).to.eql(true, 'expected variable filter to be created');
        expect(createCallback.calledOnce).to.eql(true, 'expected onFilterAdded callback to be called');
      });

      it('should disable the complete button if the filter is invalid', () => {
        update({variables});

        const createBtn = selectByText(
          footNode.querySelectorAll('button'),
          'Create Filter'
        )[0];

        expect(createBtn.getAttribute('disabled')).to.be.ok;
      });
    });
  });
});
