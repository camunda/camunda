import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import {createAnalysisSelection, __set__, __ResetDependency__} from 'main/processDisplay/views/analytics/AnalysisSelection';
import React from 'react';
import {mount} from 'enzyme';
import sinon from 'sinon';
import {createReactMock} from 'testHelpers';

chai.use(chaiEnzyme());

const {expect} = chai;
const jsx = React.createElement;

describe('<AnalysisSelection>', () => {
  let node;
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

    AnalysisInput = createReactMock('AnalysisInput', true);
    __set__('AnalysisInput', AnalysisInput);

    getNameForElement = sinon.stub().returns('SomeEndEvent');

    AnalysisSelection = createAnalysisSelection(getNameForElement);

    node = mount(<AnalysisSelection {...state} />);
  });

  afterEach(() => {
    __ResetDependency__('AnalysisInput');
  });

  it('should contain two Analysis inputs', () => {
    expect(node).to.contain.text('AnalysisInputAnalysisInput');
  });

  it('should give a type, a label and the selection to the input', () => {
    const childState = AnalysisInput.getProperty('selection', 0);

    expect(childState.type).to.eql('EndEvent');
    expect(childState.label.trim()).to.eql('End Event');
    expect(childState.name).to.eql('SomeEndEvent');
    expect(childState.hovered).to.eql(true);
  });
});
