import {expect} from 'chai';
import {reducer, createLoadingHeatmapResultAction, createLoadingDiagramResultAction, INITIAL_STATE} from 'main/processDisplay/diagram/diagram.reducer';

describe('diagram reducer', () => {
  const diagramData = 'bpmnXml';
  const heatmapData = {a: 1};

  describe('initial', () => {
    let id,
        state,
        heatmap;

    beforeEach(() => {
      ({id, state, heatmap} = reducer(undefined, {type: '@@INIT'}));
    });

    it('should set diagram id on state', () => {
      expect(id).to.exist;
      expect(typeof id).to.eql('string');
    });

    it('should set diagram state on state', () => {
      expect(state).to.exist;
      expect(state).to.eql(INITIAL_STATE);
    });

    it('should set heatmap on state', () => {
      expect(heatmap).to.exist;
      expect(typeof heatmap).to.eql('object');
      expect(heatmap.state).to.exist;
      expect(heatmap.state).to.eql(INITIAL_STATE);
    });
  });

  describe('actions', () => {
    it('should set the diagram xml property on load diagram result action', () => {
      const {xml} = reducer(undefined, createLoadingDiagramResultAction(diagramData));

      expect(xml).to.exist;
      expect(xml).to.eql(diagramData);
    });

    it('should set heatmap data property on load heatmap result action', () => {
      const {heatmap: {data}} = reducer(undefined, createLoadingHeatmapResultAction(heatmapData));

      expect(data).to.exist;
      expect(data).to.eql(heatmapData);
    });
  });
});
