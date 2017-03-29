import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {expect} from 'chai';
import {createAnalysisSelection, __set__, __ResetDependency__} from 'main/processDisplay/controls/analysisSelection/AnalysisSelection';

describe('<AnalysisSelection>', () => {
  let node;
  let update;
  let state;

  let AnalysisSelection;
  let AnalysisInput;

  const integrator = 'INTEGRATOR';

  beforeEach(() => {
    state = {
      gateway: 'SomeGateway',
      endEvent: 'SomeEndEvent'
    };

    AnalysisInput = createMockComponent('AnalysisInput', true);
    __set__('AnalysisInput', AnalysisInput);

    AnalysisSelection = createAnalysisSelection(integrator);

    ({node, update} = mountTemplate(<AnalysisSelection />));
    update(state);
  });

  afterEach(() => {
    __ResetDependency__('AnalysisInput');
  });

  it('should contain two Analysis inputs', () => {
    expect(node.textContent).to.eql('AnalysisInputAnalysisInput');
  });

  it('should expose the analysis input nodes', () => {
    expect(AnalysisSelection.nodes.EndEvent).to.exist;
    expect(AnalysisSelection.nodes.Gateway).to.exist;
  });

  it('should give a type, a label and the selection to the input', () => {
    const childState = AnalysisInput.calls[0][0].selector(state);

    expect(childState.type).to.eql('EndEvent');
    expect(childState.label).to.eql('End Event');
    expect(childState.name).to.eql('SomeEndEvent');
  });

  it('should pass the integrator to AnalysisInput', () => {
    expect(AnalysisInput.calls[0][0].integrator).to.eql(integrator);
  });
});
