import {jsx} from 'view-utils';
import {mountTemplate, triggerEvent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {ValueSelection, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/variable/ValueSelection';

describe('<ValueSelection>', () => {
  let node;
  let update;
  let addValue;
  let removeValue;
  let setValue;
  let operatorCanHaveMultipleValues;
  let variables;

  beforeEach(() => {
    variables = {
      data: [
        {values:['a', 'b', 'c']}
      ]
    };

    addValue = sinon.spy();
    __set__('addValue', addValue);

    setValue = sinon.spy();
    __set__('setValue', setValue);

    removeValue = sinon.spy();
    __set__('removeValue', removeValue);

    operatorCanHaveMultipleValues = sinon.stub().returns(true);
    __set__('operatorCanHaveMultipleValues', operatorCanHaveMultipleValues);

    ({node, update} = mountTemplate(<ValueSelection />));

    update({variables, selectedIdx: 0, operator: '=', value: []});
  });

  afterEach(() => {
    __ResetDependency__('addValue');
    __ResetDependency__('removeValue');
    __ResetDependency__('setValue');
    __ResetDependency__('operatorCanHaveMultipleValues');
  });

  it('should contain a list of values', () => {
    expect(node.textContent).to.include('abc');
  });

  it('should have the current value checked', () => {
    update({variables, selectedIdx: 0, operator: '=', value: ['b']});

    const checkboxes = node.querySelectorAll('input[type="checkbox"]');

    expect(checkboxes[1].checked).to.eql(true);
  });

  it('should add the value when user clicks on checkbox', () => {
    const cCheckbox = node.querySelectorAll('input[type="checkbox"]')[2];

    cCheckbox.checked = true;
    triggerEvent({
      node: cCheckbox,
      eventName: 'click'
    });

    expect(addValue.calledWith('c')).to.eql(true);
  });

  it('should remove a already selected value when the user clicks the checkbox', () => {
    const cCheckbox = node.querySelectorAll('input[type="checkbox"]')[2];

    cCheckbox.checked = false;
    triggerEvent({
      node: cCheckbox,
      eventName: 'click'
    });

    expect(removeValue.calledWith('c')).to.eql(true);
  });

  it('should display radio buttons if multiple values are not allowed', () => {
    operatorCanHaveMultipleValues.returns(false);

    update({variables, selectedIdx: 0, operator: '=', value: ['b']});

    const radios = node.querySelectorAll('input[type="radio"]');

    expect(radios).to.not.be.empty;
  });

  it('should set selected value when the user clicks the radio button', () => {
    operatorCanHaveMultipleValues.returns(false);

    update({variables, selectedIdx: 0, operator: '=', value: ['b']});

    const cRadio = node.querySelectorAll('input[type="radio"]')[2];

    cRadio.checked = true;
    triggerEvent({
      node: cRadio,
      eventName: 'click'
    });

    expect(setValue.calledWith('c')).to.eql(true);
  });
});
