import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {createSelectedNodeDiagram, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/executedNode/SelectNodeDiagram';

describe('main/processDisplay/controls/filter/executedNode <SelectNodeDiagram>', () => {
  let resetZoom;
  let Loader;
  let onNextTick;
  let isBpmnType;
  let setElementVisibility;
  let ViewerCall;
  let viewer;
  let onSelectionChange;
  let SelectNodeDiagram;
  let node;
  let update;

  beforeEach(() => {
    resetZoom = sinon.spy();
    __set__('resetZoom', resetZoom);

    Loader = createMockComponent('Loader', true);
    __set__('Loader', Loader);

    onNextTick = sinon.stub().callsArg(0);
    __set__('onNextTick', onNextTick);

    isBpmnType = sinon.stub().returns(false);
    __set__('isBpmnType', isBpmnType);

    setElementVisibility = sinon.spy();
    __set__('setElementVisibility', setElementVisibility);

    ViewerCall = sinon.spy();
    const Viewer = function(...args) {
      ViewerCall(...args);

      viewer = this;

      viewer.on = sinon.spy();
      viewer.get = sinon.stub().returnsThis();
      viewer.hasMarker = sinon.stub();
      viewer.removeMarker = sinon.stub();
      viewer.addMarker = sinon.stub();
      viewer.filter = sinon.stub();
      viewer.forEach = sinon.spy();
      viewer.importXML = sinon.stub().callsArg(1);
    };

    __set__('Viewer', Viewer);

    onSelectionChange = sinon.spy();

    SelectNodeDiagram = createSelectedNodeDiagram();

    ({node, update} = mountTemplate(<SelectNodeDiagram onSelectionChange={onSelectionChange} />));
  });

  afterEach(() => {
    __ResetDependency__('resetZoom');
    __ResetDependency__('Loader');
    __ResetDependency__('onNextTick');
    __ResetDependency__('isBpmnType');
    __ResetDependency__('setElementVisibility');
    __ResetDependency__('Viewer');
  });

  it('should render the Loader', () => {
    expect(node).to.contain.text(Loader.text);
  });

  it('should have diagram__holder node', () => {
    expect(node).to.contain('.diagram__holder');
  });

  it('should construct Viewer with right container node', () => {
    const container = ViewerCall.firstCall.args[0].container;

    expect(container).to.eql(node.querySelector('.diagram__holder'));
  });

  it('should update selected from state', () => {
    const element = {
      id: 'id-1'
    };
    const element2 = {
      id: 'K#233'
    };
    const element3 = {
      id: 'a3f467cd'
    };
    const selected = [element.id, element3.id];

    viewer.hasMarker.returns(false);
    update(selected);

    const [iteratee] = viewer.forEach.firstCall.args;

    iteratee(element);

    viewer.hasMarker.returns(true);
    iteratee(element2);

    viewer.hasMarker.returns(false);
    iteratee(element3);

    expect(viewer.addMarker.calledWith(element.id)).to.eql(true);
    expect(viewer.removeMarker.calledWith(element2.id)).to.eql(true);
    expect(viewer.removeMarker.calledWith(element3.id)).to.eql(false);
    expect(viewer.removeMarker.calledWith(element3.id)).to.eql(false);
  });

  describe('on element.click', () => {
    let listener;
    let element;

    beforeEach(() => {
      ([, listener] = viewer.on.firstCall.args);
      element = {
        id: 'el-id-01'
      };

      viewer.filter.returns([
        {
          id: 'd1',
          businessObject: {
            name: 'd1'
          }
        }
      ]);
    });

    it('should check bpmn type of element', () => {
      listener({element});

      expect(isBpmnType.calledWith(element)).to.eql(true);
    });

    it('should remove marker if there is one for element', () => {
      viewer.hasMarker.returns(true);
      listener({element});

      expect(viewer.removeMarker.calledWith(element.id)).to.eql(true);
    });

    it('should add market if there is none for element', () => {
      viewer.hasMarker.returns(false);
      listener({element});

      expect(viewer.addMarker.calledWith(element.id)).to.eql(true);
    });

    it('should call onSelectionChange with new elements', () => {
      listener({element});

      expect(
        onSelectionChange.calledWith([{
          id: 'd1',
          name: 'd1'
        }])
      ).to.eql(true);
    });
  });

  describe('loadDiagram', () => {
    let xml;

    beforeEach(() => {
      xml = 'some bpmn xml';

      SelectNodeDiagram.loadDiagram(xml);
    });

    it('should load xml', () => {
      expect(viewer.importXML.calledWith(xml)).to.eql(true);
    });

    it('should load xml', () => {
      SelectNodeDiagram.loadDiagram(xml);

      expect(viewer.importXML.calledOnce).to.eql(true);
    });

    it('should hide Loader', () => {
      const loaderNode = Loader.getChildrenNode();

      expect(setElementVisibility.calledWith(loaderNode, false)).to.eql(true);
    });

    it('should add element-selectable marker to selectable elements', () => {
      const id = 'dd2';
      const [iteratee] = viewer.forEach.firstCall.args;

      iteratee({id});

      expect(isBpmnType.calledWith({id})).to.eql(true);
      expect(viewer.addMarker.calledWith(id, 'element-selectable')).to.eql(true);
    });
  });
});
