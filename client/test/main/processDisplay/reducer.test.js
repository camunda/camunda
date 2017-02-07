import {expect} from 'chai';
import {reducer, createLoadingHeatmapResultAction, createLoadingDiagramResultAction} from 'main/processDisplay/reducer';
import {createSelectProcessDefinitionAction} from 'main/processDisplay/controls/processDefinition/reducer';
import {INITIAL_STATE, LOADED_STATE} from 'utils/loading';

describe('processDisplay reducer', () => {
  const diagramData = 'bpmnXml';
  const heatmapData = {a: 1};

  it('should produce state with display property', () => {
    const state = reducer(undefined, {type: '@@INIT'});

    expect(state.display).to.exist;
  });

  describe('initial', () => {
    let diagram;
    let heatmap;

    beforeEach(() => {
      ({display: {diagram, heatmap}} = reducer(undefined, {type: '@@INIT'}));
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
      const {display: {diagram: {data}}} = reducer(undefined, createLoadingDiagramResultAction(diagramData));

      expect(data).to.exist;
      expect(data).to.eql(diagramData);
    });

    it('should set heatmap data property on load heatmap result action', () => {
      const {display: {heatmap: {data}}} = reducer(undefined, createLoadingHeatmapResultAction(heatmapData));

      expect(data).to.exist;
      expect(data).to.eql(heatmapData);
    });

    it('should reset the diagram and heatmap to initial state when process definition changes', () => {
      const prevState = {
        display: {
          diagram: {
            state: LOADED_STATE
          },
          heatmap: {
            state: LOADED_STATE
          }
        }
      };
      const {display: {heatmap, diagram}} = reducer(prevState, createSelectProcessDefinitionAction('aProcess'));

      expect(heatmap.state).to.eql(INITIAL_STATE);
      expect(diagram.state).to.eql(INITIAL_STATE);
    });
  });
});
