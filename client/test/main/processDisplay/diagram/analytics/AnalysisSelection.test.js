import {jsx} from 'view-utils';
import sinon from 'sinon';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {expect} from 'chai';
import {createAnalysisSelection, __set__, __ResetDependency__} from 'main/processDisplay/diagram/analytics/AnalysisSelection';

describe('<AnalysisSelection>', () => {
  let node;
  let update;
  let state;

  let AnalysisSelection;
  let AnalysisInput;
  let getNameForElement;

  beforeEach(() => {
    state = {
      selection: {
        Gateway: 'SomeGateway',
        EndEvent: 'SomeEndEvent'
      },
      hover: {
        EndEvent: true
      }
    };

    AnalysisInput = createMockComponent('AnalysisInput', true);
    __set__('AnalysisInput', AnalysisInput);

    getNameForElement = sinon.stub().returns('SomeEndEvent');

    AnalysisSelection = createAnalysisSelection(getNameForElement);

    ({node, update} = mountTemplate(<AnalysisSelection />));
    update(state);
  });

  afterEach(() => {
    __ResetDependency__('AnalysisInput');
  });

  it('should contain two Analysis inputs', () => {
    expect(node.textContent).to.eql('AnalysisInputAnalysisInput');
  });

  it('should give a type, a label and the selection to the input', () => {
    const childState = AnalysisInput.getAttribute('selector')(state);

    expect(childState.type).to.eql('EndEvent');
    expect(childState.label.trim()).to.eql('End Event');
    expect(childState.name).to.eql('SomeEndEvent');
    expect(childState.hovered).to.eql(true);
  });
});
