import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {ProcessDisplay, __set__, __ResetDependency__} from 'main/processDisplay/ProcessDisplay';
import {INITIAL_STATE, LOADED_STATE, LOADING_STATE} from 'utils/loading';

describe('<ProcessDisplay>', () => {
  let ProcessDefinition;
  let Filter;
  let CreateFilter;
  let Diagram;
  let loadDiagram;
  let loadHeatmap;
  let node;
  let update;

  beforeEach(() => {
    ProcessDefinition = createMockComponent('ProcessDefinition');
    __set__('ProcessDefinition', ProcessDefinition);

    Filter = createMockComponent('Filter');
    __set__('Filter', Filter);

    CreateFilter = createMockComponent('CreateFilter');
    __set__('CreateFilter', CreateFilter);

    Diagram = createMockComponent('Diagram');
    __set__('HeatmapDiagram', Diagram);

    loadDiagram = sinon.spy();
    __set__('loadDiagram', loadDiagram);

    loadHeatmap = sinon.spy();
    __set__('loadHeatmap', loadHeatmap);

    ({node, update} = mountTemplate(<ProcessDisplay selector="processDisplay"/>));
  });

  afterEach(() => {
    __ResetDependency__('ProcessDefinition');
    __ResetDependency__('Filter');
    __ResetDependency__('CreateFilter');
    __ResetDependency__('HeatmapDiagram');
    __ResetDependency__('loadDiagram');
    __ResetDependency__('loadHeatmap');
  });

  it('should contain diagram section', () => {
    expect(node.querySelector('.diagram')).to.exist;
  });

  it('should not do anything when no process definition is set', () => {
    update({processDisplay: {processDefinition: {
      availableProcessDefinitions: {state: LOADED_STATE}
    }, display: {
      diagram: {state: INITIAL_STATE},
      heatmap: {state: INITIAL_STATE}
    }, filter: {
      query: []
    }}});

    expect(loadDiagram.called).to.eql(false);
    expect(loadHeatmap.called).to.eql(false);
  });

  it('should load the diagram when the process definition is set', () => {
    update({processDisplay: {
      processDefinition: {
        selected: 'definition',
        availableProcessDefinitions: {state: LOADED_STATE}
      },
      display: {
        diagram: {state: INITIAL_STATE},
        heatmap: {state: INITIAL_STATE}
      }, filter: {
        query: []
      }
    }});

    expect(loadDiagram.calledWithMatch({processDefinitionId: 'definition'})).to.eql(true);
  });

  it('should not load the diagram when it is already loaded', () => {
    update({processDisplay: {
      processDefinition: {
        selected: 'definition',
        availableProcessDefinitions: {state: LOADED_STATE}
      },
      display: {
        diagram: {state: LOADED_STATE},
        heatmap: {state: LOADED_STATE}
      }, filter: {
        query: []
      }
    }});

    expect(loadDiagram.called).to.eql(false);
  });

  it('should load the heatmap when the process definition is set', () => {
    update({processDisplay: {
      processDefinition: {
        selected: 'definition',
        availableProcessDefinitions: {state: LOADED_STATE}
      },
      display: {
        diagram: {state: INITIAL_STATE},
        heatmap: {state: INITIAL_STATE}
      }, filter: {
        query: []
      }
    }});

    expect(loadHeatmap.calledWithMatch({processDefinitionId: 'definition'})).to.eql(true);
  });

  it('should not load the heatmap when it is already loaded', () => {
    update({processDisplay: {
      processDefinition: {
        selected: 'definition',
        availableProcessDefinitions: {state: LOADED_STATE}
      },
      display: {
        diagram: {state: LOADED_STATE},
        heatmap: {state: LOADED_STATE}
      }, filter: {
        query: []
      }
    }});

    expect(loadHeatmap.called).to.eql(false);
  });

  it('should display a no process definitions hint', () => {
    update({processDisplay: {
      processDefinition: {
        availableProcessDefinitions: {
          state: LOADED_STATE,
          data: []
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
      processDefinition: {
        availableProcessDefinitions: {
          state: LOADED_STATE,
          data: ['procDef1', 'procDef2']
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
      processDefinition: {
        selected: 'definition',
        availableProcessDefinitions: {state: LOADED_STATE}
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
