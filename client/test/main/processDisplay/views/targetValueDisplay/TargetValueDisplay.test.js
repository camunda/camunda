import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import {TargetValueDisplay, __set__, __ResetDependency__} from 'main/processDisplay/views/targetValueDisplay/TargetValueDisplay';
import React from 'react';
import {mount} from 'enzyme';
import sinon from 'sinon';
import {createReactMock} from 'testHelpers';

chai.use(chaiEnzyme());

const {expect} = chai;
const jsx = React.createElement;

describe('<TargetValueDisplay>', () => {
  let node;
  let createOverlaysRenderer;
  let TargetValueModal;
  let Diagram;

  beforeEach(() => {
    createOverlaysRenderer = sinon.spy();
    __set__('createOverlaysRenderer', createOverlaysRenderer);

    Diagram = createReactMock('Diagram');
    Diagram.getViewer = sinon.spy();
    __set__('Diagram', Diagram);

    TargetValueModal = createReactMock('TargetValueModal');
    __set__('TargetValueModal', TargetValueModal);

    node = mount(<TargetValueDisplay targetValue={{}} />);
  });

  afterEach(() => {
    __ResetDependency__('createOverlaysRenderer');
    __ResetDependency__('TargetValueModal');
    __ResetDependency__('createStateComponent');
    __ResetDependency__('Diagram');
    __ResetDependency__('getDefinitionId');
  });

  it('should create an overlay renderer', () => {
    expect(createOverlaysRenderer.called).to.eql(true);
  });

  it('should contain the passed Diagram component', () => {
    expect(node).to.contain.text(Diagram.text);
  });

  it('should contain the TargetValueModal', () => {
    expect(node).to.contain.text(TargetValueModal.text);
  });
});
