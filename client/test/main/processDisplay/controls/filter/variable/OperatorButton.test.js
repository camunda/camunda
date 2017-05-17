import {jsx} from 'view-utils';
import {mountTemplate, triggerEvent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {OperatorButton, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/variable/OperatorButton';

describe('<OperatorButton>', () => {
  let node;
  let update;
  let setOperator;
  let setValue;
  let labels;

  beforeEach(() => {
    setValue = sinon.spy();
    __set__('setValue', setValue);

    setOperator = sinon.spy();
    __set__('setOperator', setOperator);

    labels = {
      T: 'TEST',
      V: 'VALUE'
    };
    __set__('labels', labels);
  });

  afterEach(() => {
    __ResetDependency__('setValue');
    __ResetDependency__('setOperator');
    __ResetDependency__('labels');
  });

  it('should display the label of the supplied operator', () => {
    ({node, update} = mountTemplate(<OperatorButton operator="T" />));
    update({});

    expect(node.textContent).to.contain(labels.T);
  });

  it('should call setOperator when clicked', () => {
    ({node, update} = mountTemplate(<OperatorButton operator="T" />));
    update({});

    triggerEvent({
      node,
      selector: 'button',
      eventName: 'click'
    });

    expect(setOperator.calledWith('T')).to.eql(true);
  });

  it('should display label of the implicit value if provided', () => {
    ({node, update} = mountTemplate(<OperatorButton operator="T" implicitValue="V" />));
    update({});

    expect(node.textContent).to.contain(labels.T + ' ' + labels.V);
  });

  it('should call setValue if implicit value is provided', () => {
    ({node, update} = mountTemplate(<OperatorButton operator="T" implicitValue="V" />));
    update({});

    triggerEvent({
      node,
      selector: 'button',
      eventName: 'click'
    });

    expect(setValue.calledWith('V')).to.eql(true);
  });

  it('should call setValue if implicit value is provided but falsy', () => {
    ({node, update} = mountTemplate(<OperatorButton operator="T" implicitValue={false} />));
    update({});

    triggerEvent({
      node,
      selector: 'button',
      eventName: 'click'
    });

    expect(setValue.calledWith(false)).to.eql(true);
  });

  it('should have the active class if operator is active', () => {
    ({node, update} = mountTemplate(<OperatorButton operator="T" />));
    update({operator: 'T'});

    expect(node.querySelector('button').classList.contains('active')).to.eql(true);
  });

  it('should not have the active class if implicit value does not match', () => {
    ({node, update} = mountTemplate(<OperatorButton operator="T" implicitValue="V" />));
    update({operator: 'T'});

    expect(node.querySelector('button').classList.contains('active')).to.eql(false);
  });

  it('should have the active class if operator and implicit value match', () => {
    ({node, update} = mountTemplate(<OperatorButton operator="T" implicitValue="V" />));
    update({operator: 'T', value: 'V'});

    expect(node.querySelector('button').classList.contains('active')).to.eql(true);
  });
});
