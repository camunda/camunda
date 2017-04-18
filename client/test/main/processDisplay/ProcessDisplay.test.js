import {jsx, includes, withSelector} from 'view-utils';
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
  let ProcessInstanceCount;
  let TargetValueDisplay;
  let getDefinitionId;
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
        targetValue: {state: LOADED_STATE},
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

    TargetValueDisplay = createMockComponent('TargetValueDisplay');
    __set__('TargetValueDisplay', TargetValueDisplay);

    ProcessInstanceCount = createMockComponent('ProcessInstanceCount');
    __set__('ProcessInstanceCount', withSelector(ProcessInstanceCount));

    loadData = 'load-data';
    __set__('loadData', loadData);

    getDefinitionId = sinon.stub().returns('abc');
    __set__('getDefinitionId', getDefinitionId);

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
    __ResetDependency__('TargetValueDisplay');
    __ResetDependency__('ProcessInstanceCount');
    __ResetDependency__('createHeatmapDiagram');
    __ResetDependency__('createHeatmapRendererFunction');
    __ResetDependency__('createCreateAnalyticsRendererFunction');
    __ResetDependency__('createDiagramControlsIntegrator');
    __ResetDependency__('loadData');
    __ResetDependency__('loadDiagram');
    __ResetDependency__('isViewSelected');
    __ResetDependency__('getDefinitionId');
  });

  it('should load diagram xml on startup', () => {
    expect(loadDiagram.calledOnce).to.eql(true);
  });

  it('should contain diagram section', () => {
    expect(node.querySelector('.diagram')).to.exist;
  });

  it('should not contain the processInstanceCount by default', () => {
    expect(node.textContent).to.not.contain('ProcessInstanceCount');
  });

  it('should contain the processInstanceCount when heatmap mode is selected', () => {
    selectedView = 'frequency';

    update(state);

    expect(node.textContent).to.contain('ProcessInstanceCount');
  });

  it('should contain process instance count for target value view', () => {
    selectedView = 'target_value';

    update(state);

    expect(node.textContent).to.contain(ProcessInstanceCount.text);
  });

  it('should pass the processInstanceCount to the ProcessInstanceCount component', () => {
    selectedView = 'frequency';

    update(state);

    expect(ProcessInstanceCount.mocks.update.calledWith(33)).to.eql(true);
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

  it('should display a loading indicator while loading target value', () => {
    state.diagram.targetValue.state = LOADING_STATE;

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
