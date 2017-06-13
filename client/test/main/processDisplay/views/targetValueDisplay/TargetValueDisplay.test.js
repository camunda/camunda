import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {TargetValueDisplay, __set__, __ResetDependency__} from 'main/processDisplay/views/targetValueDisplay/TargetValueDisplay';

describe('<TargetValueDisplay>', () => {
  let node;
  let update;
  let createOverlaysRenderer;
  let createTargetValueModal;
  let targetValueModal;
  let stateComponent;
  let createStateComponent;
  let Diagram;
  const getProcessDefinition = () => 'asdf';

  beforeEach(() => {
    createOverlaysRenderer = sinon.spy();
    __set__('createOverlaysRenderer', createOverlaysRenderer);

    Diagram = createMockComponent('Diagram');
    Diagram.getViewer = sinon.spy();
    __set__('Diagram', Diagram);

    stateComponent = createMockComponent('State', true);
    createStateComponent = sinon.stub().returns(stateComponent);
    __set__('createStateComponent', createStateComponent);

    targetValueModal = createMockComponent('TargetValueModal');
    createTargetValueModal = sinon.stub().returns(targetValueModal);
    __set__('createTargetValueModal', createTargetValueModal);

    __set__('getDefinitionId', getProcessDefinition);

    ({node, update} = mountTemplate(<TargetValueDisplay />));
    update();
  });

  afterEach(() => {
    __ResetDependency__('createOverlaysRenderer');
    __ResetDependency__('createTargetValueModal');
    __ResetDependency__('createStateComponent');
    __ResetDependency__('Diagram');
    __ResetDependency__('getDefinitionId');
  });

  it('should create a targetValueModal with State component, process definition and getViewer function', () => {
    expect(
      createTargetValueModal.calledWith(stateComponent, getProcessDefinition, Diagram.getViewer)
    ).to.eql(true);
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
