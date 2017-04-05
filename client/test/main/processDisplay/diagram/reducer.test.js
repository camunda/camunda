import {expect} from 'chai';
import {reducer, createLoadingHeatmapResultAction, createLoadingDiagramResultAction} from 'main/processDisplay/diagram/reducer';
import {INITIAL_STATE} from 'utils/loading';

describe('diagram reducer', () => {
  const diagramData = 'bpmnXml';
  const heatmapData = {a: 1};

  describe('initial', () => {
    let bpmnXml;
    let heatmap;

    beforeEach(() => {
      ({bpmnXml, heatmap} = reducer(undefined, {type: '@@INIT'}));
    });

    it('should set bpmnXml state on state', () => {
      expect(bpmnXml).to.exist;
      expect(typeof bpmnXml).to.eql('object');
      expect(bpmnXml.state).to.exist;
      expect(bpmnXml.state).to.eql(INITIAL_STATE);
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
      const {bpmnXml: {data}} = reducer(undefined, createLoadingDiagramResultAction(diagramData));

      expect(data).to.exist;
      expect(data).to.eql(diagramData);
    });

    it('should set heatmap data property on load heatmap result action', () => {
      const {heatmap: {data}} = reducer(undefined, createLoadingHeatmapResultAction(heatmapData));

      expect(data).to.exist;
      expect(data).to.eql(heatmapData);
    });
  });
});
