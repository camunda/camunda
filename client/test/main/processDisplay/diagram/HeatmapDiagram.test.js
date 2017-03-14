import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {createHeatmapDiagram, __set__, __ResetDependency__} from 'main/processDisplay/diagram/HeatmapDiagram';

describe('<HeatmapDiagram>', () => {
  const diagramXml = 'diagram-xml';
  const flowNodes = {a: 1};
  const piCount = 7;
  const heatmapData = {flowNodes, piCount};
  const heatmapNode = document.createElement('img');

  let loadedDiagramState;
  let loadedHeatmapState;
  let getHeatmap;
  let removeHeatmapOverlay;
  let addHeatmapOverlay;
  let viewer;
  let canvas;
  let eventBus;
  let update;
  let createDiagram;
  let Diagram;
  let HeatmapDiagram;

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

    const modules = {
      canvas,
      eventBus
    };

    viewer = {
      get: (name) => {
        return modules[name];
      }
    };

    Diagram = createMockComponent('Diagram');
    Diagram.getViewer = sinon.spy();
    createDiagram = sinon.stub().returns(Diagram);
    __set__('createDiagram', createDiagram);

    HeatmapDiagram = createHeatmapDiagram();

    mountTemplate(<HeatmapDiagram selector="display" />);

    update = Diagram.calls[0][0].createOverlaysRenderer[0]({viewer});
  });

  afterEach(() => {
    __ResetDependency__('getHeatmap');
    __ResetDependency__('removeHeatmapOverlay');
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

    it('should give access to the viewer instance from the Diagram', () => {
      HeatmapDiagram.getViewer();

      expect(Diagram.getViewer.calledOnce).to.eql(true);
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

      expect(removeHeatmapOverlay.called).to.eql(true);
    });

    it('should add overlays with the loaded heatmap data', () => {
      update({
        state: loadedHeatmapState,
        diagramRendered: true
      });

      expect(addHeatmapOverlay.calledWith(viewer, flowNodes)).to.eql(true);
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
