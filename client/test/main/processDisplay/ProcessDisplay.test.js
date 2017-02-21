import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {expect} from 'chai';
import {ProcessDisplay, __set__, __ResetDependency__} from 'main/processDisplay/ProcessDisplay';
import {INITIAL_STATE, LOADED_STATE, LOADING_STATE} from 'utils/loading';

describe('<ProcessDisplay>', () => {
  let Controls;
  let Diagram;
  let loadData;
  let node;
  let update;

  beforeEach(() => {
    Controls = createMockComponent('Controls');
    __set__('Controls', Controls);

    Diagram = createMockComponent('Diagram');
    __set__('HeatmapDiagram', Diagram);

    loadData = 'load-data';
    __set__('loadData', loadData);

    ({node, update} = mountTemplate(<ProcessDisplay selector="processDisplay"/>));
  });

  afterEach(() => {
    __ResetDependency__('Controls');
    __ResetDependency__('HeatmapDiagram');
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
    update({processDisplay: {
      controls: {
        processDefinition: {
          availableProcessDefinitions: {
            state: LOADED_STATE,
            data: []
          }
        }
      },
      display: {
        diagram: {state: INITIAL_STATE},
        heatmap: {state: INITIAL_STATE}
      }, filter: {
        query: []
      }
    }});
    expect(node.querySelector('.help_screen .no_definitions')).to.not.be.null;
  });

  it('should display a select process definition hint', () => {
    update({processDisplay: {
      controls: {
        processDefinition: {
          availableProcessDefinitions: {
            state: LOADED_STATE,
            data: ['procDef1', 'procDef2']
          }
        }
      },
      display: {
        diagram: {state: INITIAL_STATE},
        heatmap: {state: INITIAL_STATE}
      }, filter: {
        query: []
      }
    }});
    expect(node.querySelector('.help_screen .process_definition')).to.not.be.null;
  });

  it('should display a loading indicator while loading', () => {
    update({processDisplay: {
      controls: {
        processDefinition: {
          selected: 'definition',
          availableProcessDefinitions: {state: LOADED_STATE}
        }
      },
      display: {
        diagram: {state: LOADING_STATE},
        heatmap: {state: LOADING_STATE}
      }, filter: {
        query: []
      }
    }});

    expect(node.querySelector('.loading_indicator')).to.not.be.null;
  });
});
