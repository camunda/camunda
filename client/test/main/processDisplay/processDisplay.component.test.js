import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {ProcessDisplay, __set__, __ResetDependency__} from 'main/processDisplay/processDisplay.component';
import {INITIAL_STATE, LOADED_STATE} from 'utils/loading';

describe('<ProcessDisplay>', () => {
  let ProcessDefinition;
  let Diagram;
  let loadDiagram;
  let loadHeatmap;
  let node;
  let update;

  beforeEach(() => {
    ProcessDefinition = createMockComponent('ProcessDefinition');
    __set__('ProcessDefinition', ProcessDefinition);

    Diagram = createMockComponent('Diagram');
    __set__('Diagram', Diagram);

    loadDiagram = sinon.spy();
    __set__('loadDiagram', loadDiagram);

    loadHeatmap = sinon.spy();
    __set__('loadHeatmap', loadHeatmap);

    ({node, update} = mountTemplate(<ProcessDisplay selector="processDisplay"/>));
  });

  afterEach(() => {
    __ResetDependency__('ProcessDefinition');
    __ResetDependency__('Diagram');
    __ResetDependency__('loadDiagram');
    __ResetDependency__('loadHeatmap');
  });

  it('should display <Diagram> component', () => {
    expect(node).to.contain.text('Diagram');
  });

  it('should not do anything when no process definition is set', () => {
    update({processDisplay: {processDefinition: {}}});

    expect(loadDiagram.called).to.eql(false);
    expect(loadHeatmap.called).to.eql(false);
  });

  it('should load the diagram when the process definition is set', () => {
    update({processDisplay: {
      processDefinition: {
        selected: 'definition'
      },
      display: {
        diagram: {state: INITIAL_STATE},
        heatmap: {state: INITIAL_STATE}
      }
    }});

    expect(loadDiagram.calledWithMatch({id: 'definition'})).to.eql(true);
  });

  it('should not load the diagram when it is already loaded', () => {
    update({processDisplay: {
      processDefinition: {
        selected: 'definition'
      },
      display: {
        diagram: {state: LOADED_STATE},
        heatmap: {state: LOADED_STATE}
      }
    }});

    expect(loadDiagram.called).to.eql(false);
  });

  it('should load the heatmap when the process definition is set', () => {
    update({processDisplay: {
      processDefinition: {
        selected: 'definition'
      },
      display: {
        diagram: {state: INITIAL_STATE},
        heatmap: {state: INITIAL_STATE}
      }
    }});

    expect(loadHeatmap.calledWithMatch({id: 'definition'})).to.eql(true);
  });

  it('should not load the heatmap when it is already loaded', () => {
    update({processDisplay: {
      processDefinition: {
        selected: 'definition'
      },
      display: {
        diagram: {state: LOADED_STATE},
        heatmap: {state: LOADED_STATE}
      }
    }});

    expect(loadHeatmap.called).to.eql(false);
  });
});
