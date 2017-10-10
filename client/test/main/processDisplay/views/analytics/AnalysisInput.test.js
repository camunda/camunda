import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import {AnalysisInput, __set__, __ResetDependency__} from 'main/processDisplay/views/analytics/AnalysisInput';
import React from 'react';
import {mount} from 'enzyme';
import sinon from 'sinon';

chai.use(chaiEnzyme());

const {expect} = chai;
const jsx = React.createElement;

describe('<AnalysisInput>', () => {
  let node;
  let state;
  let unsetElement;
  let addHighlight;
  let removeHighlights;

  const type = 'INPUT_TYPE';
  const label = 'INPUT_LABEL';
  const name = 'SELECTION_NAME';
  const controlsName = 'CONTROLS_NAME';

  beforeEach(() => {
    unsetElement = sinon.spy();
    __set__('unsetElement', unsetElement);

    addHighlight = sinon.spy();
    __set__('addHighlight', addHighlight);

    removeHighlights = sinon.spy();
    __set__('removeHighlights', removeHighlights);

    state = {
      name: controlsName,
      selection: {
        type,
        name,
        label
      }
    };

    node = mount(<AnalysisInput {...state} />);
  });

  afterEach(() => {
    __ResetDependency__('unsetElement');
    __ResetDependency__('addHighlight');
    __ResetDependency__('removeHighlights');
  });

  it('should display a list item', () => {
    expect(node.find('li')).to.exist;
  });

  it('should display a hint to select something when nothing is selected', () => {
    state.selection.name = undefined;
    node.setProps({selection: state.selection});

    expect(node).to.contain.text('Please Select');
  });

  it('should display the name of the selected element when something is selected', () => {
    expect(node).to.contain.text(name);
  });

  it('should unset the displayed selection', () => {
    node.find('button').simulate('click');

    expect(unsetElement.calledWith(type)).to.eql(true);
  });

  it('should call the addHighlight when hovering over the field', () => {
    node.find('li').simulate('mouseover');

    expect(addHighlight.calledWith(type)).to.eql(true);
  });

  it('should call the removeHighlights when no longer hovering over the field', () => {
    node.find('li').simulate('mouseout');

    expect(removeHighlights.called).to.eql(true);
  });
});
