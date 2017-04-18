import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {TargetValueDisplay, __set__, __ResetDependency__} from 'main/processDisplay/diagram/targetValueDisplay/TargetValueDisplay';

describe('<TargetValueDisplay>', () => {
  let node;
  let update;
  let createOverlaysRenderer;
  let createTargetValueModal;
  let targetValueModal;
  let stateComponent;
  let createStateComponent;
  let Diagram;
  const processDefinition = 'asdf';

  beforeEach(() => {
    createOverlaysRenderer = sinon.spy();
    __set__('createOverlaysRenderer', createOverlaysRenderer);

    Diagram = createMockComponent('Diagram');

    stateComponent = createMockComponent('State', true);
    createStateComponent = sinon.stub().returns(stateComponent);
    __set__('createStateComponent', createStateComponent);

    targetValueModal = createMockComponent('TargetValueModal');
    createTargetValueModal = sinon.stub().returns(targetValueModal);
    __set__('createTargetValueModal', createTargetValueModal);

    ({node, update} = mountTemplate(<TargetValueDisplay Diagram={Diagram} processDefinition={processDefinition} />));
    update();
  });

  afterEach(() => {
    __ResetDependency__('createOverlaysRenderer');
    __ResetDependency__('createTargetValueModal');
    __ResetDependency__('createStateComponent');
  });

  it('should create a targetValueModal with State component and process definition', () => {
    expect(createTargetValueModal.calledWith(stateComponent, processDefinition)).to.eql(true);
  });

  it('should create an overlay renderer with the state component and the target value modal component', () => {
    expect(createOverlaysRenderer.calledWith(stateComponent, targetValueModal));
  });

  it('should contain the passed Diagram component', () => {
    expect(node.textContent).to.contain(Diagram.text);
  });

  it('should contain the targetValueModal', () => {
    expect(node.textContent).to.contain(targetValueModal.text);
  });
});
