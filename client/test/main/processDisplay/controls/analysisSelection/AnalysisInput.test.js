import {jsx} from 'view-utils';
import {mountTemplate, triggerEvent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {AnalysisInput} from 'main/processDisplay/controls/analysisSelection/AnalysisInput';

describe('<AnalysisInput>', () => {
  let node;
  let update;
  let state;

  let integrator;
  const type = 'INPUT_TYPE';
  const label = 'INPUT_LABEL';
  const name = 'SELECTION_NAME';

  beforeEach(() => {
    integrator = {
      unset: sinon.spy(),
      hover: sinon.spy(),
      unhover: sinon.spy()
    };

    state = {
      integrator,
      type,
      name,
      label
    };

    ({node, update} = mountTemplate(<AnalysisInput />));
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

  it('should unset the displayed selection through the integrator', () => {
    update(state);
    triggerEvent({
      node,
      selector: 'button',
      eventName: 'click'
    });

    expect(integrator.unset.calledWith(type)).to.eql(true);
  });

  it('should call the hover handler of the integrator when hovering over the field', () => {
    update(state);
    triggerEvent({
      node,
      selector: 'li',
      eventName: 'mouseover'
    });

    expect(integrator.hover.calledWith(type, true)).to.eql(true);
  });

  it('should call the unhover handler of the integrator when no longer hovering over the field', () => {
    update(state);
    triggerEvent({
      node,
      selector: 'li',
      eventName: 'mouseout'
    });

    expect(integrator.unhover.calledWith(type, true)).to.eql(true);
  });
});
