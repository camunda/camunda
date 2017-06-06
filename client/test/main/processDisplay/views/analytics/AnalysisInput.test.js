import {jsx} from 'view-utils';
import {mountTemplate, triggerEvent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {AnalysisInput, __set__, __ResetDependency__} from 'main/processDisplay/views/analytics/AnalysisInput';

describe('<AnalysisInput>', () => {
  let node;
  let update;
  let state;
  let unsetElement;
  let addHighlight;
  let removeHighlights;

  const type = 'INPUT_TYPE';
  const label = 'INPUT_LABEL';
  const name = 'SELECTION_NAME';

  beforeEach(() => {
    unsetElement = sinon.spy();
    __set__('unsetElement', unsetElement);

    addHighlight = sinon.spy();
    __set__('addHighlight', addHighlight);

    removeHighlights = sinon.spy();
    __set__('removeHighlights', removeHighlights);

    state = {
      type,
      name,
      label
    };

    ({node, update} = mountTemplate(<AnalysisInput />));
  });

  afterEach(() => {
    __ResetDependency__('unsetElement');
    __ResetDependency__('addHighlight');
    __ResetDependency__('removeHighlights');
  });

  it('should display a list item', () => {
    update(state);
    expect(node.querySelector('li')).to.exist;
  });

  it('should display a hint to select something when nothing is selected', () => {
    state.name = undefined;
    update(state);

    expect(node.textContent).to.contain('Please Select');
  });

  it('should display the name of the selected element when something is selected', () => {
    update(state);
    expect(node.textContent).to.contain(name);
  });

  it('should unset the displayed selection', () => {
    update(state);
    triggerEvent({
      node,
      selector: 'button',
      eventName: 'click'
    });

    expect(unsetElement.calledWith(type)).to.eql(true);
  });

  it('should call the addHighlight when hovering over the field', () => {
    update(state);
    triggerEvent({
      node,
      selector: 'li',
      eventName: 'mouseover'
    });

    expect(addHighlight.calledWith(type)).to.eql(true);
  });

  it('should call the removeHighlights when no longer hovering over the field', () => {
    update(state);
    triggerEvent({
      node,
      selector: 'li',
      eventName: 'mouseout'
    });

    expect(removeHighlights.called).to.eql(true);
  });
});
