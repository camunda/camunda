import {expect} from 'chai';
import {reducer, createLoadingHeatmapResultAction, createLoadingDiagramResultAction} from 'main/processDisplay/diagram/diagram.reducer';

describe('diagram reducer', () => {
  const diagramData = 'bpmnXml';
  const heatmapData = {a: 1};

  let state;

  describe('initial', () => {
    beforeEach(() => {
      state = reducer(undefined, {type: '@@INIT'});
    });

    it('should set diagram id on state', () => {
      expect(state.id).to.exist;
      expect(typeof state.id).to.eql('string');
    });

    it('should set diagram state on state', () => {
      expect(state.state).to.exist;
      expect(typeof state.state).to.eql('string');
    });

    it('should set heatmap on state', () => {
      expect(state.heatmap).to.exist;
      expect(typeof state.heatmap).to.eql('object');
    });
  });

  describe('actions', () => {
    it('should set the diagram xml property on load diagram result action', () => {
      state = reducer(undefined, createLoadingDiagramResultAction(diagramData));

      expect(state.xml).to.exist;
    });

    it('should set heatmap data property on load heatmap result action', () => {
      state = reducer(undefined, createLoadingHeatmapResultAction(heatmapData));

      expect(state.heatmap.data).to.exist;
    });
  });
});
