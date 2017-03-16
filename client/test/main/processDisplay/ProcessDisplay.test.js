import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {ProcessDisplay, __set__, __ResetDependency__} from 'main/processDisplay/ProcessDisplay';
import {LOADED_STATE, LOADING_STATE} from 'utils/loading';

describe('<ProcessDisplay>', () => {
  let Controls;
  let createHeatmapDiagram;
  let createDiagram;
  let Diagram;
  let HeatmapDiagram;
  let Statistics;
  let loadData;
  let node;
  let update;
  let state;

  beforeEach(() => {
    state = {
      controls: {
        processDefinition: {
          selected: 'definition',
          availableProcessDefinitions: {
            state: LOADED_STATE,
            data: ['procDef1', 'procDef2']
          }
        },
        view: 'none'
      },
      display: {
        diagram: {state: LOADED_STATE},
        heatmap: {state: LOADED_STATE, data: {
          piCount: 33
        }},
      }, filter: {
        query: []
      }
    };

    Controls = createMockComponent('Controls');
    __set__('Controls', Controls);

    HeatmapDiagram = createMockComponent('HeatmapDiagram');
    createHeatmapDiagram = sinon.stub().returns(HeatmapDiagram);
    __set__('createHeatmapDiagram', createHeatmapDiagram);

    Diagram = createMockComponent('Diagram');
    createDiagram = sinon.stub().returns(Diagram);
    __set__('createDiagram', createDiagram);

    Statistics = createMockComponent('Statistics');
    __set__('Statistics', Statistics);

    loadData = 'load-data';
    __set__('loadData', loadData);

    ({node, update} = mountTemplate(<ProcessDisplay />));
  });

  afterEach(() => {
    __ResetDependency__('Controls');
    __ResetDependency__('Statistics');
    __ResetDependency__('createHeatmapDiagram');
    __ResetDependency__('createDiagram');
    __ResetDependency__('loadDiagram');
    __ResetDependency__('loadHeatmap');
  });

  it('should contain diagram section', () => {
    expect(node.querySelector('.diagram')).to.exist;
  });

  it('should pass loadData to Controls component as onCriteriaChanged attribute', () => {
    expect(Controls.getAttribute('onCriteriaChanged')).to.eql(loadData);
  });

  it('should display a no process definitions hint', () => {
    state.controls.processDefinition.availableProcessDefinitions.data.length = 0;
    update(state);

    expect(node.querySelector('.help_screen .no_definitions')).to.not.be.null;
  });

  it('should display a select process definition hint', () => {
    state.controls.processDefinition.selected = null;
    update(state);

    expect(node.querySelector('.help_screen .process_definition')).to.not.be.null;
  });

  it('should display a loading indicator while loading', () => {
    state.display.diagram.state = LOADING_STATE;
    state.display.heatmap.state = LOADING_STATE;
    update(state);

    expect(node.querySelector('.loading_indicator')).to.not.be.null;
  });

  it('should display the heatmap diagram when the frequency view mode is selected', () => {
    state.controls.view = 'frequency';
    update(state);

    expect(node.textContent).to.contain('HeatmapDiagram');
  });

  it('should display a no data indicator if the heatmap data contains no process instances', () => {
    state.controls.view = 'frequency';
    state.display.heatmap.data.piCount = 0;
    update(state);

    expect(node.querySelector('.no-data-indicator')).to.exist;
  });

  it('should not display a no data indicator outside the frequency heatmap mode', () => {
    state.display.heatmap.data.piCount = 0;
    update(state);

    expect(node.querySelector('.no-data-indicator')).to.not.exist;
  });

  it('should display the normal bpmn diagram when the "none" view mode is selected', () => {
    update(state);

    expect(node.textContent).to.not.contain('HeatmapDiagram');
    expect(node.textContent).to.contain('Diagram');
  });
});
