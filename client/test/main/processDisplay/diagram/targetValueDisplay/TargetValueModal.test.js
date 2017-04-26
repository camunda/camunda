import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent, triggerEvent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {createTargetValueModal, __set__, __ResetDependency__} from 'main/processDisplay/diagram/targetValueDisplay/TargetValueModal';

describe('<TargetValueModal>', () => {
  let node;
  let update;
  let TargetValueModal;
  let State;
  let setTargetDurationToForm;
  let getTargetDurationFromForm;
  let setTargetValue;
  let TargetValueInput;
  let Modal;
  let createModal;
  let Socket;
  let getTargetValue;
  let onNextTick;
  let saveTargetValues;

  let element;

  const getProcessDefinition = () => 'asdf';
  const ELEMENT_NAME = 'ELEMENT_NAME';
  const TARGET_VALUE = 400;

  beforeEach(() => {
    State = {
      getState: sinon.stub().returns({targetValue: {data: {}}})
    };

    element = {
      businessObject: {
        name: ELEMENT_NAME
      }
    };

    Modal = createMockComponent('Modal', true);
    Modal.open = sinon.spy();
    Modal.close = sinon.spy();

    createModal = sinon.stub().returns(Modal);
    __set__('createModal', createModal);

    Socket = createMockComponent('Socket', true);
    __set__('Socket', Socket);

    setTargetDurationToForm = sinon.spy();
    __set__('setTargetDurationToForm', setTargetDurationToForm);

    getTargetDurationFromForm = sinon.stub().returns(TARGET_VALUE);
    __set__('getTargetDurationFromForm', getTargetDurationFromForm);

    getTargetValue = sinon.stub().returns(TARGET_VALUE);
    __set__('getTargetValue', getTargetValue);

    setTargetValue = sinon.spy();
    __set__('setTargetValue', setTargetValue);

    TargetValueInput = createMockComponent('TargetValueInput');
    __set__('TargetValueInput', TargetValueInput);

    onNextTick = sinon.stub().callsArg(0);
    __set__('onNextTick', onNextTick);

    saveTargetValues = sinon.spy();
    __set__('saveTargetValues', saveTargetValues);

    TargetValueModal = createTargetValueModal(State, getProcessDefinition);

    ({node, update} = mountTemplate(<TargetValueModal />));
    update();

    TargetValueModal.open(element);
  });

  afterEach(() => {
    __ResetDependency__('setTargetDurationToForm');
    __ResetDependency__('getTargetDurationFromForm');
    __ResetDependency__('setTargetValue');
    __ResetDependency__('TargetValueInput');
    __ResetDependency__('createModal');
    __ResetDependency__('getTargetValue');
    __ResetDependency__('Socket');
    __ResetDependency__('onNextTick');
    __ResetDependency__('saveTargetValues');
  });

  it('should have an open function', () => {
    expect(TargetValueModal.open).to.be.a('function');
  });

  it('should set the target duration to the form from the element', () => {
    expect(setTargetDurationToForm.calledWith(node.querySelector('.form-group'), TARGET_VALUE)).to.eql(true);
  });

  it('should set the name of the element it is opened for in the modal title', () => {
    expect(node.querySelector('.modal-title').textContent).to.contain(ELEMENT_NAME);
  });

  it('should have input fields for the duration', () => {
    expect(node.textContent).to.include(TargetValueInput.text);
  });

  it('should set the targetValue when confirming the change', () => {
    triggerEvent({
      node,
      selector: '.btn-primary',
      eventName: 'click'
    });

    expect(setTargetValue.calledWith(element, TARGET_VALUE)).to.eql(true);
  });

  it('should close the Modal when confirming the change', () => {
    triggerEvent({
      node,
      selector: '.btn-primary',
      eventName: 'click'
    });

    expect(Modal.close.calledOnce).to.eql(true);
  });

  it('should have a button to set the form to 0', () => {
    triggerEvent({
      node,
      selector: '.form-group .btn',
      eventName: 'click'
    });

    expect(setTargetDurationToForm.calledWith(node.querySelector('.form-group'), 0)).to.eql(true);
  });
});
