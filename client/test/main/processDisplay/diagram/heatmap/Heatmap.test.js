import {expect} from 'chai';
import sinon from 'sinon';
import {createHeatmapRendererFunction, __set__, __ResetDependency__} from 'main/processDisplay/diagram/heatmap/Heatmap';

describe('<Heatmap>', () => {
  const diagramXml = 'diagram-xml';
  const flowNodes = {a: 1};
  const piCount = 7;
  const heatmapData = {flowNodes, piCount};
  const heatmapNode = document.createElement('img');

  let loadedDiagramState;
  let loadedHeatmapState;
  let getHeatmap;
  let addHeatmapOverlay;
  let viewer;
  let canvas;
  let eventBus;
  let removeOverlays;
  let update;
  let formatter;

  beforeEach(() => {
    loadedDiagramState = {
      diagram: {
        state: 'LOADED',
        data: diagramXml
      },
      heatmap: {
        state: 'INITIAL'
      }
    };

    loadedHeatmapState = {
      diagram: {
        state: 'LOADED',
        data: diagramXml
      },
      heatmap: {
        state: 'LOADED',
        data: heatmapData
      }
    };

    getHeatmap = sinon.stub().returns(heatmapNode);
    __set__('getHeatmap', getHeatmap);

    removeOverlays = sinon.spy();
    __set__('removeOverlays', removeOverlays);

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

    const modules = {
      canvas,
      eventBus
    };

    viewer = {
      get: (name) => {
        return modules[name];
      }
    };

    formatter = x => x;

    update = createHeatmapRendererFunction(formatter)({viewer});
  });

  afterEach(() => {
    __ResetDependency__('getHeatmap');
    __ResetDependency__('removeOverlays');
    __ResetDependency__('addHeatmapOverlay');
    __ResetDependency__('Diagram');
    __ResetDependency__('createDiagram');
  });

  describe('heatmap processing', () => {
    beforeEach(() => {
      update({
        state: loadedHeatmapState,
        diagramRendered: true
      });
    });

    it('should construct a heatmap', () => {
      expect(getHeatmap.calledWith(viewer, flowNodes))
        .to.eql(true, 'expected getHeatmap to be called with the viewer instance and flownode data');
    });

    it('should add a heatmap', () => {
      expect(canvas._viewport.appendChild.calledWith(heatmapNode)).to.eql(true, 'expected heatmap to be attached to viewport node');
    });

    it('should do nothing when diagram is not rendered', () => {
      getHeatmap.reset();

      update({
        state: loadedHeatmapState,
        diagramRendered: false
      });

      expect(getHeatmap.called).to.eql(false);
    });
  });

  describe('heatmap overlays', () => {
    it('should not add heatmap overlays if the heatmap data is not loaded', () => {
      update({
        state: loadedDiagramState,
        diagramRendered: true
      });

      expect(addHeatmapOverlay.called).to.eql(false);
    });

    it('should remove overlays', () => {
      update({
        state: loadedHeatmapState,
        diagramRendered: true
      });

      expect(removeOverlays.called).to.eql(true);
    });

    it('should add overlays with the loaded heatmap data', () => {
      update({
        state: loadedHeatmapState,
        diagramRendered: true
      });

      expect(addHeatmapOverlay.calledWith(viewer, flowNodes, formatter)).to.eql(true);
    });

    it('should only render heatmap once', () => {
      update({
        state: {
          ...loadedHeatmapState,
          hovered: 'element'
        },
        diagramRendered: true
      });
      update({
        state: {
          ...loadedHeatmapState,
          hovered: 'anotherElement'
        },
        diagramRendered: true
      });

      expect(getHeatmap.calledOnce).to.eql(true);
    });
  });
});
