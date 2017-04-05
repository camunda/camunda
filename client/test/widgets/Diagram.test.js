import {jsx} from 'view-utils';
import {mountTemplate} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {createDiagram, __set__, __ResetDependency__} from 'widgets/Diagram';

describe('<Diagram>', () => {
  const diagramXml = 'diagram-xml';

  let initialState;
  let loadedDiagramState;
  let Viewer;
  let viewer;
  let diagramNode;
  let canvas;
  let eventBus;
  let update;
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

    eventBus = {
      on: sinon.spy()
    };

    Viewer = function({container}) {
      const modules = {
        canvas,
        eventBus
      };

      diagramNode = container;
      viewer = this;

      this.get = function(name) {
        return modules[name];
      };

      this.importXML = sinon.stub().callsArg(1);
    };
    __set__('Viewer', Viewer);

    renderOverlays = sinon.spy();
    createOverlaysRenderer = sinon.stub().returns(renderOverlays);

    Diagram = createDiagram();

    ({update, node} = mountTemplate(
      <Diagram createOverlaysRenderer={createOverlaysRenderer} />
    ));
  });

  afterEach(() => {
    __ResetDependency__('Viewer');
  });

  it('should provide access to the bpmn-viewer instance', () => {
    expect(Diagram.getViewer()).to.exist;
    expect(Diagram.getViewer().constructor.name).to.eql('Viewer');
  });

  it('should display the loader', () => {
    expect(node).to.contain('.diagram-loading');
    expect(node.querySelector('.diagram-loading').style.display).to.eql('');
  });

  it('should hide the loader after diagram is loaded', () => {
    update(loadedDiagramState);

    expect(node.querySelector('.diagram-loading').style.display).to.eql('none');
  });

  describe('overlays', () => {
    it('should create overlays renderer with viewer, node and eventsBus', () => {
      const options = createOverlaysRenderer.lastCall.args[0];

      expect(options.viewer).to.equal(viewer);
      expect(options.node).to.equal(diagramNode);
      expect(options).to.contain.key('eventsBus');
    });

    it('should call renderOverlays on update', () => {
      update(loadedDiagramState);

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

      ({update} = mountTemplate(
        <Diagram createOverlaysRenderer={[createOverlaysRenderer1, createOverlaysRenderer2]} />
      ));

      update(loadedDiagramState);

      expect(createOverlaysRenderer1.called).to.eql(true);
      expect(createOverlaysRenderer2.called).to.eql(true);
      expect(renderOverlays1.called).to.eql(true);
      expect(renderOverlays2.called).to.eql(true);
    });
  });

  describe('initial state', () => {
    beforeEach(() => {
      update(initialState);
    });

    it('should pass diagram__holder node to Viewer constructor', () => {
      expect(diagramNode).to.have.class('diagram__holder');
    });
  });

  describe('loaded state', () => {
    beforeEach(() => {
      update(loadedDiagramState);
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
