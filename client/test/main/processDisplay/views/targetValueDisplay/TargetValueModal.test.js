import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import {TargetValueModal, __set__, __ResetDependency__} from 'main/processDisplay/views/targetValueDisplay/TargetValueModal';
import React from 'react';
import {mount} from 'enzyme';
import sinon from 'sinon';
import {createReactMock} from 'testHelpers';
import {noop} from 'view-utils';

chai.use(chaiEnzyme());

const {expect} = chai;
const jsx = React.createElement;

describe('<TargetValueModal>', () => {
  let node;
  let setTargetValue;
  let TargetValueInput;
  let Modal;
  let onNextTick;
  let saveTargetValues;
  let getDefinitionId;

  let element;

  const ELEMENT_NAME = 'ELEMENT_NAME';

  beforeEach(() => {
    element = {
      businessObject: {
        name: ELEMENT_NAME
      }
    };

    Modal = createReactMock('Modal', true);
    Modal.Header = createReactMock('ModalHeader', true);
    Modal.Body = createReactMock('ModalBody', true);
    Modal.Footer = createReactMock('ModalFooter', true);
    __set__('Modal', Modal);

    setTargetValue = sinon.spy();
    __set__('setTargetValue', setTargetValue);

    TargetValueInput = createReactMock('TargetValueInput');
    __set__('TargetValueInput', TargetValueInput);

    onNextTick = sinon.stub().callsArg(0);
    __set__('onNextTick', onNextTick);

    saveTargetValues = sinon.spy();
    __set__('saveTargetValues', saveTargetValues);

    getDefinitionId = sinon.spy();
    __set__('getDefinitionId', getDefinitionId);
  });

  afterEach(() => {
    __ResetDependency__('setTargetValue');
    __ResetDependency__('TargetValueInput');
    __ResetDependency__('Modal');
    __ResetDependency__('onNextTick');
    __ResetDependency__('saveTargetValues');
    __ResetDependency__('getDefinitionId');
  });

  it('should set the name of the element it is opened for in the modal title', () => {
    node = mount(<TargetValueModal element={element} />);
    expect(node.find('.modal-title')).to.contain.text(ELEMENT_NAME);
  });

  it('should have input fields for the duration', () => {
    node = mount(<TargetValueModal element={element} />);
    expect(node).to.contain.text(TargetValueInput.text);
  });

  it('should set the targetValue when confirming the change', () => {
    node = mount(<TargetValueModal close={noop} element={element} />);
    node.setState({s: 1});

    node.find('.btn-primary').simulate('click');

    expect(setTargetValue.calledWith(element, 1000)).to.eql(true);
  });

  it('should close the Modal when confirming the change', () => {
    const onClose = sinon.spy();

    node = mount(<TargetValueModal close={onClose} />);

    node.find('.btn-primary').simulate('click');

    expect(onClose.calledOnce).to.eql(true);
  });

  it('should have a button to set the form to 0', () => {
    node = mount(<TargetValueModal />);

    node.find('.form-group .btn').simulate('click');

    expect(node.state().s).to.eql(0);
  });
});
