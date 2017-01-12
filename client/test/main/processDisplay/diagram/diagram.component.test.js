import {jsx} from 'view-utils';
import {mountTemplate} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {setupPromiseMocking} from 'testHelpers';
import {Diagram, __set__, __ResetDependency__} from 'main/processDisplay/diagram/diagram.component';

const dayInMs = 24 * 60 * 60 * 1000;

describe('<Diagram>', () => {
  const diagram = 'p1';
  const diagramXml = 'diagram-xml';
  const heatmapNode = document.createElement('img');
  let getDiagramXml;
  let getHeatmap;
  let filters;
  let Viewer;
  let viewer;
  let diagramNode;
  let overlays;
  let canvas;
  let elements;
  let elementRegistry;
  let update;

  setupPromiseMocking();

  beforeEach(() => {
    getDiagramXml = sinon.stub().returns(Promise.resolve(diagramXml));
    __set__('getDiagramXml', getDiagramXml);

    getHeatmap = sinon.stub().returns(Promise.resolve(heatmapNode));
    __set__('getHeatmap', getHeatmap);

    overlays = {
      add: sinon.spy(),
      remove: sinon.spy()
    };

    canvas = {
      resized: sinon.spy(),
      zoom: sinon.spy(),
      _viewport: {
        appendChild: sinon.spy(),
        removeChild: sinon.spy()
      }
    };

    elements = [
      {id: 'id-23'}
    ];

    elementRegistry = {
      filter: sinon.stub().returns(elements)
    };

    filters = {
      startDate: 1,
      endDate: 5 * dayInMs
    };

    Viewer = function({container}) {
      const modules = {
        overlays,
        canvas,
        elementRegistry
      };

      diagramNode = container;
      viewer = this;

      this.get = function(name) {
        return modules[name];
      };

      this.importXML = sinon.stub().callsArg(1);
    };
    __set__('Viewer', Viewer);

    ({update} = mountTemplate(<Diagram />));

    update({diagram, filters});
    Promise.runAll();
  });

  afterEach(() => {
    __ResetDependency__('getDiagramXml');
    __ResetDependency__('getHeatmap');
    __ResetDependency__('Viewer');
  });

  it('should pass diagram__holder node to Viewer constructor', () => {
    expect(diagramNode).to.have.class('diagram__holder');
  });

  it('should import xml on update', () => {
    expect(viewer.importXML.calledWith(diagramXml)).to.eql(true);
  });

  it('should reset zoom after importing xml', () => {
    expect(canvas.resized.calledOnce).to.eql(true, 'expected canvas.resized to be called');
    expect(canvas.zoom.calledWith('fit-viewport', 'auto'))
      .to.eql(true, 'expected canvas.zoom to be called with "fit-viewport", "auto"');
  });

  it('should add a heatmap', () => {
    expect(canvas._viewport.appendChild.calledOnce).to.eql(true, 'expected heatmap to be attached to viewport node');
  });

  describe('after first xml load', () => {
    it('should not import xml when diagram did not change', () => {
      const filters = {
        startDate: 1,
        endDate: 7 * dayInMs
      };

      viewer.importXML.reset();

      update({diagram, filters});

      expect(viewer.importXML.called).to.eql(false);
    });

    it('should import xml when diagram changed', () => {
      const diagram = 'p2';

      viewer.importXML.reset();

      update({diagram, filters});
      Promise.runAll();

      expect(viewer.importXML.calledWith(diagramXml)).to.eql(true);
    });

    it('should remove old heatmap', () => {
      const filters = {
        startDate: 1,
        endDate: 7 * dayInMs
      };

      update({diagram, filters});
      Promise.runAll();

      expect(canvas._viewport.removeChild.calledWith(heatmapNode)).to.eql(true);
    });

    it('should update heatmap only when state changed', () => {
      update({diagram, filters});

      expect(canvas._viewport.removeChild.called).to.eql(false, 'expected heatmap not to be removed');
    });
  });
});
