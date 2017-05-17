import {jsx} from 'view-utils';
import {mountTemplate, triggerEvent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {VariableFilter, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/variable/VariableFilter';

describe('<VariableFilter>', () => {
  let node;
  let update;
  let state;
  let callback;
  let operatorLabels;

  const name = 'A';
  const type = 'String';
  const operator = '<';
  const value = 41;

  beforeEach(() => {
    state = [
      name,
      type,
      operator,
      [value]
    ];

    callback = sinon.spy();

    operatorLabels = {
      '<': 'KLEINER ALS'
    };
    __set__('operatorLabels', operatorLabels);

    ({node, update} = mountTemplate(<VariableFilter onDelete={callback}/>));

    update(state);
  });

  afterEach(() => {
    __ResetDependency__('operatorLabels');
  });

  it('should contain the name', () => {
    expect(node.textContent).to.contain(name);
  });

  it('should contain the value', () => {
    expect(node.textContent).to.contain(value);
  });

  it('should contain the label for the operator', () => {
    expect(node.textContent).to.contain(operatorLabels[operator]);
  });

  it('should call the delete callback', () => {
    triggerEvent({
      node,
      selector: 'button',
      eventName: 'click'
    });

    expect(callback.calledOnce).to.eql(true);
  });
});
