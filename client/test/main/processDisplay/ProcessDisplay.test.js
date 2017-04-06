import {jsx, includes} from 'view-utils';
import {mountTemplate, createMockComponent, observeFunction} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {ProcessDisplay, __set__, __ResetDependency__} from 'main/processDisplay/ProcessDisplay';
import {LOADED_STATE, LOADING_STATE} from 'utils/loading';

describe('<ProcessDisplay>', () => {
  let Controls;
  let createHeatmapRenderer;
  let createAnalyticsRenderer;
  let createCreateAnalyticsRendererFunction;
  let createDiagramControlsIntegrator;
  let Statistics;
  let loadData;
  let Diagram;
  let node;
  let update;
  let state;
  let loadDiagram;
  let selectedView;
  let isViewSelected;

  beforeEach(() => {
    state = {
      controls: {
        view: 'none'
      },
      diagram: {
        bpmnXml: {state: LOADED_STATE},
        heatmap: {state: LOADED_STATE, data: {
          piCount: 33
        }},
      }, filter: {
        query: []
      }
    };

    createHeatmapRenderer = sinon.spy();
    __set__('createHeatmapRendererFunction', sinon.stub().returns(createHeatmapRenderer));

    createAnalyticsRenderer = sinon.spy();
    createCreateAnalyticsRendererFunction = sinon.stub().returns(createAnalyticsRenderer);
    __set__('createCreateAnalyticsRendererFunction', createCreateAnalyticsRendererFunction);

    Controls = createMockComponent('Controls');
    Diagram = createMockComponent('Diagram');

    createDiagramControlsIntegrator = sinon.stub().returns({Controls, Diagram});
    __set__('createDiagramControlsIntegrator', createDiagramControlsIntegrator);

    Statistics = createMockComponent('Statistics');
    __set__('Statistics', Statistics);

    loadData = 'load-data';
    __set__('loadData', loadData);

    loadDiagram = sinon.spy();
    __set__('loadDiagram', loadDiagram);

    isViewSelected = observeFunction(view =>
      includes(
        [].concat(view),
        selectedView
      )
    );
    __set__('isViewSelected', isViewSelected);

    ({node, update} = mountTemplate(<ProcessDisplay />));
  });

  afterEach(() => {
    __ResetDependency__('Statistics');
    __ResetDependency__('createHeatmapDiagram');
    __ResetDependency__('createHeatmapRendererFunction');
    __ResetDependency__('createCreateAnalyticsRendererFunction');
    __ResetDependency__('createDiagramControlsIntegrator');
    __ResetDependency__('loadData');
    __ResetDependency__('loadDiagram');
    __ResetDependency__('isViewSelected');
  });

  it('should load diagram xml on starup', () => {
    expect(loadDiagram.calledOnce).to.eql(true);
  });

  it('should contain diagram section', () => {
    expect(node.querySelector('.diagram')).to.exist;
  });

  it('should pass loadData to Controls component as onCriteriaChanged attribute', () => {
    expect(Controls.getAttribute('onCriteriaChanged')).to.eql(loadData);
  });

  it('should display a loading indicator while loading', () => {
    state.diagram.bpmnXml.state = LOADING_STATE;
    state.diagram.heatmap.state = LOADING_STATE;
    update(state);

    expect(node.querySelector('.loading_indicator')).to.not.be.null;
  });

  it('should display a diagram when the frequency view mode is selected', () => {
    Diagram.reset();

    selectedView = 'frequency';

    update(state);

    expect(node.textContent).to.contain('Diagram');
    expect(Diagram.appliedWith({
      createOverlaysRenderer: createHeatmapRenderer
    })).to.eql(true);
  });

  it('should display a diagram when the duration view mode is selected', () => {
    Diagram.reset();

    selectedView = 'duration';

    update(state);

    expect(node.textContent).to.contain('Diagram');
    expect(Diagram.appliedWith({
      createOverlaysRenderer: createHeatmapRenderer
    })).to.eql(true);
  });

  it('should display a diagram when the branch analysis view mode is selected', () => {
    Diagram.reset();

    selectedView = 'branch_analysis';
    update(state);

    expect(node.textContent).to.contain('Diagram');
    expect(Diagram.appliedWith({
      selector: 'diagram',
      createOverlaysRenderer: createAnalyticsRenderer
    })).to.eql(true);
  });

  it('should display a no data indicator if the heatmap data contains no process instances', () => {
    selectedView = 'frequency';
    state.diagram.heatmap.data.piCount = 0;
    update(state);

    expect(node.querySelector('.no-data-indicator')).to.exist;
  });

  it('should display the normal bpmn diagram when the "none" view mode is selected', () => {
    update(state);

    expect(node.textContent).to.contain('Diagram');
  });
});
