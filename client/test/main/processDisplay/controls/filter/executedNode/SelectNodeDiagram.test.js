import React from 'react';
import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import sinon from 'sinon';
import {createReactMock} from 'testHelpers';
import {SelectNodeDiagram, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/executedNode/SelectNodeDiagram';
import {mount} from 'enzyme';

chai.use(chaiEnzyme());

const jsx = React.createElement;
const {expect} = chai;

describe('main/processDisplay/controls/filter/executedNode <SelectNodeDiagram>', () => {
  let Loader;
  let resetZoom;
  let onNextTick;
  let isBpmnType;
  let setElementVisibility;
  let ViewerCall;
  let viewer;
  let onSelectionChange;
  let wrapper;

  beforeEach(() => {
    Loader = createReactMock('Loader');
    __set__('Loader', Loader);

    resetZoom = sinon.spy();
    __set__('resetZoom', resetZoom);

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

    wrapper = mount(<SelectNodeDiagram diagramVisible={false} onSelectionChange={onSelectionChange} />);
  });

  afterEach(() => {
    __ResetDependency__('Loader');
    __ResetDependency__('resetZoom');
    __ResetDependency__('onNextTick');
    __ResetDependency__('isBpmnType');
    __ResetDependency__('setElementVisibility');
    __ResetDependency__('Viewer');
  });

  it('should render the Loader', () => {
    expect(wrapper).to.contain.text(Loader.text);
  });

  it('should have diagram__holder node', () => {
    expect(wrapper.find('.diagram__holder')).to.exist;
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

      wrapper = mount(<SelectNodeDiagram xml={xml} diagramVisible={true} onSelectionChange={onSelectionChange} />);
    });

    it('should load xml', () => {
      expect(viewer.importXML.calledWith(xml)).to.eql(true);
    });

    it('should load xml', () => {
      expect(viewer.importXML.calledOnce).to.eql(true);
    });

    it('should hide loader', () => {
      expect(Loader.calledWith({visible: false})).to.eql(true);
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
