import {jsx} from 'view-utils';
import {mountTemplate} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {Diagram, __set__, __ResetDependency__} from 'main/processDisplay/diagram/Diagram';

describe('<Diagram>', () => {
  const diagramXml = 'diagram-xml';
  const heatmapData = {a: 1};
  const heatmapNode = document.createElement('img');

  let initialState;
  let loadedDiagramState;
  let loadedHeatmapState;
  let getHeatmap;
  let removeHeatmapOverlay;
  let addHeatmapOverlay;
  let Viewer;
  let viewer;
  let diagramNode;
  let canvas;
  let eventBus;
  let update;

  beforeEach(() => {
    initialState = {display: {
      diagram: {
        state: 'INITIAL'
      },
      heatmap: {
        state: 'INITIAL'
      }
    }};

    loadedDiagramState = {display: {
      diagram: {
        state: 'LOADED',
        data: diagramXml
      },
      heatmap: {
        state: 'INITIAL'
      }
    }};

    loadedHeatmapState = {display: {
      diagram: {
        state: 'LOADED',
        data: diagramXml
      },
      heatmap: {
        state: 'LOADED',
        data: heatmapData
      }
    }};

    getHeatmap = sinon.stub().returns(heatmapNode);
    __set__('getHeatmap', getHeatmap);

    removeHeatmapOverlay = sinon.spy();
    __set__('removeHeatmapOverlay', removeHeatmapOverlay);

    addHeatmapOverlay = sinon.spy();
    __set__('addHeatmapOverlay', addHeatmapOverlay);

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

    ({update} = mountTemplate(<Diagram selector="display" />));
  });

  afterEach(() => {
    __ResetDependency__('getHeatmap');
    __ResetDependency__('removeHeatmapOverlay');
    __ResetDependency__('addHeatmapOverlay');
    __ResetDependency__('Viewer');
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

  describe('heatmap processing', () => {
    beforeEach(() => {
      update(loadedHeatmapState);
    });

    it('should construct a heatmap', () => {
      expect(getHeatmap.calledWith(viewer, heatmapData))
        .to.eql(true, 'expected getHeatmap to be called with the viewer instance and heatmap data');
    });

    it('should add a heatmap', () => {
      expect(canvas._viewport.appendChild.calledWith(heatmapNode)).to.eql(true, 'expected heatmap to be attached to viewport node');
    });
  });

  describe('heatmap overlays', () => {
    it('should not add heatmap overlays if the heatmap data is not loaded', () => {
      update(loadedDiagramState);

      expect(addHeatmapOverlay.called).to.eql(false);
    });

    it('should remove overlays', () => {
      update(loadedHeatmapState);

      expect(removeHeatmapOverlay.called).to.eql(true);
    });

    it('should add overlays with the loaded heatmap data and the hovered element', () => {
      update({
        display: {
          ...loadedHeatmapState.display,
          hovered: 'element'
        }
      });

      expect(addHeatmapOverlay.calledWith(viewer, heatmapData, 'element')).to.eql(true);
    });

    it('should only render heatmap once', () => {
      update({
        display: {
          ...loadedHeatmapState.display,
          hovered: 'element'
        }
      });
      update({
        display: {
          ...loadedHeatmapState.display,
          hovered: 'anotherElement'
        }
      });

      expect(getHeatmap.calledOnce).to.eql(true);
    });
  });
});
