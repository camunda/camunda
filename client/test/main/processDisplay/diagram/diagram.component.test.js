import {jsx} from 'view-utils';
import {mountTemplate} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {Diagram, __set__, __ResetDependency__} from 'main/processDisplay/diagram/diagram.component';

describe('<Diagram>', () => {
  const diagramXml = 'diagram-xml';
  const heatmapNode = document.createElement('img');
  const initialState = {diagram: {
    state: 'INITIAL'
  }};
  const loadedDiagramState = {diagram: {
    state: 'LOADED',
    xml: diagramXml,
    heatmap: {
      state: 'INITIAL'
    }
  }};
  const loadedHeatmapState = {diagram: {
    state: 'LOADED',
    xml: diagramXml,
    heatmap: {
      state: 'LOADED',
      data: {a: 1}
    }
  }};

  let loadDiagram;
  let loadHeatmap;
  let getHeatmap;
  let Viewer;
  let viewer;
  let diagramNode;
  let canvas;
  let update;

  beforeEach(() => {
    loadDiagram = sinon.spy();
    __set__('loadDiagram', loadDiagram);

    loadHeatmap = sinon.spy();
    __set__('loadHeatmap', loadHeatmap);

    getHeatmap = sinon.stub().returns(heatmapNode);
    __set__('getHeatmap', getHeatmap);

    canvas = {
      resized: sinon.spy(),
      zoom: sinon.spy(),
      _viewport: {
        appendChild: sinon.spy(),
        removeChild: sinon.spy()
      }
    };

    Viewer = function({container}) {
      const modules = {
        canvas
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
  });

  afterEach(() => {
    __ResetDependency__('loadDiagram');
    __ResetDependency__('loadHeatmap');
    __ResetDependency__('getHeatmap');
    __ResetDependency__('Viewer');
  });

  describe('initial state', () => {
    beforeEach(() => {
      update(initialState);
    });

    it('should pass diagram__holder node to Viewer constructor', () => {
      expect(diagramNode).to.have.class('diagram__holder');
    });

    it('should load xml initially', () => {
      expect(loadDiagram.calledOnce).to.eql(true);
    });

    it('should display a loading indicator', () => {
      expect(diagramNode.querySelector('.loading_indicator')).to.not.be.null;
    });
  });

  describe('diagram loading', () => {
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

    it('should load the heatmap data', () => {
      expect(loadHeatmap.calledOnce).to.eql(true);
    });
  });

  describe('heatmap processing', () => {
    beforeEach(() => {
      update(loadedHeatmapState);
    });

    it('should construct a heatmap', () => {
      expect(getHeatmap.calledOnce).to.eql(true);
    });

    it('should add a heatmap', () => {
      expect(canvas._viewport.appendChild.calledWith(heatmapNode)).to.eql(true, 'expected heatmap to be attached to viewport node');
    });
  });
});
