import React from 'react';
import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import sinon from 'sinon';
import {createReactMock} from 'testHelpers';
import {ExecutedNodeModal, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/executedNode/ExecutedNodeModal';
import {mount} from 'enzyme';

chai.use(chaiEnzyme());

const jsx = React.createElement;
const {expect} = chai;

describe('main/processDisplay/controls/filter/executedNode <ExecutedNodeModal>', () => {
  let Modal;
  let SelectNodeDiagram;
  let addFlowNodesFilter;
  let onFilterAdded;
  let getDiagramXML;
  let onNextTick;
  let filterType;
  let setProperty;
  let wrapper;

  beforeEach(() => {
    Modal = createReactMock('Modal', true);
    __set__('Modal', Modal);

    Modal.Header = createReactMock('Modal.Header', true);
    Modal.Body = createReactMock('Modal.Body', true);
    Modal.Footer = createReactMock('Modal.Footer', true);

    SelectNodeDiagram = createReactMock('SelectNodeDiagram');
    __set__('SelectNodeDiagram', SelectNodeDiagram);

    addFlowNodesFilter = sinon.spy();
    __set__('addFlowNodesFilter', addFlowNodesFilter);

    onFilterAdded = sinon.spy();
    getDiagramXML = sinon.stub().returns('diagram');

    onNextTick = sinon.stub().callsArg(0);
    __set__('onNextTick', onNextTick);

    filterType = 'filter-type';
    __set__('filterType', filterType);

    setProperty = sinon.spy();

    wrapper = mount(
      <ExecutedNodeModal setProperty={setProperty}
                         isOpen={true}
                         onFilterAdded={onFilterAdded}
                         getDiagramXML={getDiagramXML} />
    );
  });

  afterEach(() => {
    __ResetDependency__('Modal');
    __ResetDependency__('SelectNodeDiagram');
    __ResetDependency__('addFlowNodesFilter');
    __ResetDependency__('onNextTick');
    __ResetDependency__('filterType');
  });

  it('should loadDiagram on Modal open', () => {
    const open = Modal.getProperty('onEntered');

    SelectNodeDiagram.reset();
    open();

    expect(
      SelectNodeDiagram.calledWith({
        diagramVisible: true,
        xml: 'diagram'
      })
    ).to.eql(true, 'expected diagram to be made visible');
  });

  it('should add close button', () => {
    expect(wrapper.find('button.close')).to.exist;
  });

  it('should call Modal.close on close button clicked', () => {
    setProperty.reset(); // just to make sure it wasn't called before
    wrapper.find('button.close').simulate('click');

    expect(setProperty.calledWith('isOpen', false)).to.eql(true);
  });

  it('should contain selected node diagram', () => {
    expect(wrapper).to.contain.text(SelectNodeDiagram.text);
  });

  it('should pass onSelectionChange callback to SelectNodeDiagram', () => {
    const onSelectionChange = SelectNodeDiagram.getProperty('onSelectionChange');
    const nodes = 'nodes';

    expect(onSelectionChange.bind(null, nodes)).not.to.throw;
  });

  it('should display Abort and Create Filter buttons', () => {
    expect(wrapper).to.contain.text('Abort');
    expect(wrapper).to.contain.text('Create Filter');
  });

  it('should close Modal on abort', () => {
    wrapper.find('button.btn-default').simulate('click');

    expect(setProperty.calledWith('isOpen', false)).to.eql(true);
  });

  it('should close Modal and change selected nodes on Create Filter', () => {
    const onSelectionChange = SelectNodeDiagram.getProperty('onSelectionChange');
    const nodes = 'nodes' + Math.random();

    onSelectionChange(nodes);
    wrapper.find('button.btn-primary').simulate('click');

    expect(addFlowNodesFilter.calledWith(nodes))
      .to.eql(true, 'expected selected nodes to be changed');
    expect(setProperty.calledWith('isOpen', false))
      .to.eql(true, 'expected Modal to be closed');
    expect(onNextTick.calledWith(onFilterAdded))
      .to.eql(true, 'expected onFilterAdded to be executed on next tick');
    expect(onFilterAdded.called)
      .to.eql(true, 'expected onFilterAdded to be called');
  });
});
