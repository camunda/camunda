import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import {createDiagram, __set__, __ResetDependency__} from 'widgets/Diagram';
import sinon from 'sinon';
import React from 'react';
import {mount} from 'enzyme';

chai.use(chaiEnzyme());

const {expect} = chai;
const jsx = React.createElement;

describe('<Diagram>', () => {
  const diagramXml = 'diagram-xml';

  let initialState;
  let loadedDiagramState;
  let Viewer;
  let viewer;
  let diagramNode;
  let canvas;
  let zoomScroll;
  let eventBus;
  let isLoaded;
  let renderOverlays;
  let createOverlaysRenderer;
  let Diagram;
  let node;

  beforeEach(() => {
    initialState = {
      bpmnXml: {
        state: 'INITIAL'
      },
      heatmap: {
        state: 'INITIAL'
      }
    };

    loadedDiagramState = {
      bpmnXml: {
        state: 'LOADED',
        data: diagramXml
      },
      heatmap: {
        state: 'INITIAL'
      }
    };

    canvas = {
      resized: sinon.spy(),
      zoom: sinon.spy(),
      _viewport: {
        appendChild: sinon.spy(),
        removeChild: sinon.spy()
      }
    };

    zoomScroll = {
      zoom: sinon.spy()
    };

    eventBus = {
      on: sinon.spy()
    };

    Viewer = function({container}) {
      const modules = {
        canvas,
        eventBus,
        zoomScroll
      };

      diagramNode = container;
      viewer = this;

      this.get = function(name) {
        return modules[name];
      };

      this.importXML = sinon.stub().callsArg(1);
    };
    __set__('Viewer', Viewer);

    isLoaded = sinon.stub().returns(false);
    __set__('isLoaded', isLoaded);

    renderOverlays = sinon.spy();
    createOverlaysRenderer = sinon.stub().returns(renderOverlays);

    Diagram = createDiagram();

    node = mount(
      <Diagram createOverlaysRenderer={createOverlaysRenderer} {...initialState} />
    );
  });

  afterEach(() => {
    __ResetDependency__('Viewer');
    __ResetDependency__('isLoaded');
  });

  it('should provide access to the bpmn-viewer instance', () => {
    expect(Diagram.getViewer()).to.exist;
    expect(Diagram.getViewer().constructor.name).to.eql('Viewer');
  });

  it('should display the loader', () => {
    expect(node.find('.diagram-loading')).to.be.present();
  });

  it('should hide the loader after diagram is loaded', () => {
    node.setState({loaded: true});

    expect(node.find('.diagram-loading')).to.not.be.present();
  });

  it('should have a button to zoom in', () => {
    node.find('.zoom-in.btn').simulate('click');

    expect(zoomScroll.zoom.calledOnce).to.eql(true);
    expect(zoomScroll.zoom.getCall(0).args[0]).to.be.above(0);
  });

  it('should have a button to zoom out', () => {
    node.find('.zoom-out.btn').simulate('click');

    expect(zoomScroll.zoom.calledOnce).to.eql(true);
    expect(zoomScroll.zoom.getCall(0).args[0]).to.be.below(0);
  });

  it('should have a button to reset zoom', () => {
    node.find('.reset-zoom.btn').simulate('click');

    expect(canvas.zoom.calledOnce).to.eql(true);
    expect(canvas.zoom.calledWith('fit-viewport', 'auto')).to.eql(true);
  });

  describe('overlays', () => {
    it('should create overlays renderer with viewer and node', () => {
      const options = createOverlaysRenderer.lastCall.args[0];

      expect(options.viewer).to.equal(viewer);
      expect(options.node).to.equal(diagramNode);
    });

    it('should call renderOverlays on update', () => {
      node.setProps({...loadedDiagramState});

      expect(renderOverlays.calledWith({
        state: loadedDiagramState,
        diagramRendered: true
      }));
    });

    it('should be able to have multiple overlay renderer', () => {
      const renderOverlays1 = sinon.spy();
      const createOverlaysRenderer1 = sinon.stub().returns(renderOverlays1);
      const renderOverlays2 = sinon.spy();
      const createOverlaysRenderer2 = sinon.stub().returns(renderOverlays2);

      const Diagram = createDiagram();

      node = mount(
        <Diagram createOverlaysRenderer={[createOverlaysRenderer1, createOverlaysRenderer2]} {...initialState} />
      );

      node.setProps({...loadedDiagramState});

      expect(createOverlaysRenderer1.called).to.eql(true);
      expect(createOverlaysRenderer2.called).to.eql(true);
      expect(renderOverlays1.called).to.eql(true);
      expect(renderOverlays2.called).to.eql(true);
    });
  });

  describe('initial state', () => {
    it('should render diagram within diagram__holder node', () => {
      expect(diagramNode.parentNode).to.have.class('diagram__holder');
    });
  });

  describe('loaded state', () => {
    beforeEach(() => {
      isLoaded.returns(true);
      node.setProps({...loadedDiagramState});
    });

    it('should import xml on update', () => {
      expect(viewer.importXML.calledWith(diagramXml)).to.eql(true);
    });

    it('should reset zoom after importing xml', () => {
      expect(canvas.resized.calledOnce).to.eql(true, 'expected canvas.resized to be called');
      expect(canvas.zoom.calledWith('fit-viewport', 'auto'))
        .to.eql(true, 'expected canvas.zoom to be called with "fit-viewport", "auto"');
    });
  });
});
