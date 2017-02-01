import {expect} from 'chai';
import {reducer, createLoadingHeatmapResultAction, createLoadingDiagramResultAction,
        createHoverElementAction} from 'main/processDisplay/diagram/diagram.reducer';
import {INITIAL_STATE} from 'utils/loading';

describe('diagram reducer', () => {
  const diagramData = 'bpmnXml';
  const heatmapData = {a: 1};
  const diagramElement = {id: 'elementId'};

  describe('initial', () => {
    let diagram;
    let heatmap;

    beforeEach(() => {
      ({diagram, heatmap} = reducer(undefined, {type: '@@INIT'}));
    });

    it('should set diagram state on state', () => {
      expect(diagram).to.exist;
      expect(typeof diagram).to.eql('object');
      expect(diagram.state).to.exist;
      expect(diagram.state).to.eql(INITIAL_STATE);
    });

    it('should set heatmap on state', () => {
      expect(heatmap).to.exist;
      expect(typeof heatmap).to.eql('object');
      expect(heatmap.state).to.exist;
      expect(heatmap.state).to.eql(INITIAL_STATE);
    });
  });

  describe('actions', () => {
    it('should set the diagram data property on load diagram result action', () => {
      const {diagram: {data}} = reducer(undefined, createLoadingDiagramResultAction(diagramData));

      expect(data).to.exist;
      expect(data).to.eql(diagramData);
    });

    it('should set heatmap data property on load heatmap result action', () => {
      const {heatmap: {data}} = reducer(undefined, createLoadingHeatmapResultAction(heatmapData));

      expect(data).to.exist;
      expect(data).to.eql(heatmapData);
    });

    it('should set the hovered diagram element', () => {
      const {hovered} = reducer(undefined, createHoverElementAction(diagramElement));

      expect(hovered).to.exist;
      expect(hovered).to.eql(diagramElement.id);
    });
  });
});
