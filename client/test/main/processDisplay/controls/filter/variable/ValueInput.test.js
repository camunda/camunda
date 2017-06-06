import {jsx} from 'view-utils';
import {mountTemplate, triggerEvent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {ValueInput, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/variable/ValueInput';

describe('<ValueInput>', () => {
  let node;
  let update;
  let setValue;
  let addValue;
  let variables;
  let onNextTick;

  beforeEach(() => {
    variables = {
      data: [
        {type: 'Boolean'},
        {type: 'String'},
        {type: 'Double'}
      ]
    };

    setValue = sinon.spy();
    __set__('setValue', setValue);

    addValue = sinon.spy();
    __set__('addValue', addValue);

    onNextTick = sinon.spy();
    __set__('onNextTick', onNextTick);

    ({node, update} = mountTemplate(<ValueInput />));
  });

  afterEach(() => {
    __ResetDependency__('setValue');
    __ResetDependency__('addValue');
    __ResetDependency__('onNextTick');
  });

  it('should contain an input field', () => {
    update({variables, values: ['']});
    expect(node.querySelector('input')).to.exist;
  });

  it('should have a text input field by default', () => {
    update({
      variables,
      selectedIdx: 1,
      values: ['']
    });

    expect(node.querySelector('input').getAttribute('type')).to.eql('text');
  });

  it('should have a hidden input field for booleans', () => {
    update({
      variables,
      selectedIdx: 0,
      values: ['']
    });

    expect(node.querySelector('input').getAttribute('type')).to.eql('hidden');
  });

  it('should have a hidden input field if no variable is selected', () => {
    update({variables, values: ['']});

    expect(node.querySelector('input').getAttribute('type')).to.eql('hidden');
  });

  it('should reflect the state value in the input field', () => {
    const values = ['AAAAA'];

    update({variables, values});

    expect(node.querySelector('input').value).to.eql(values[0]);
  });

  it('should have an option to add more entries', () => {
    update({
      variables,
      selectedIdx: 1,
      values: ['something']
    });

    expect(node.textContent).to.contain('Add another value');
    expect(node.querySelector('.add-another-btn').classList.contains('hidden')).to.eql(false);
  });

  it('should not show add another value for non-strings', () => {
    update({
      variables,
      selectedIdx: 2,
      values: ['something']
    });

    expect(node.querySelector('.add-another-btn').classList.contains('hidden')).to.eql(true);
  });

  it('should not show add another value if the last value is empty', () => {
    update({
      variables,
      selectedIdx: 1,
      values: ['']
    });

    expect(node.querySelector('.add-another-btn').classList.contains('hidden')).to.eql(true);
  });

  it('should add another value when clicking on add another value button', () => {
    update({
      variables,
      selectedIdx: 1,
      values: ['something']
    });

    triggerEvent({
      node,
      selector: 'button',
      eventName: 'click'
    });

    expect(addValue.calledOnce).to.eql(true);
  });

  it('should set the value if the user changes it', () => {
    const newValue = 'newValue';

    update({variables, selectedIdx: 1, values: ['']});
    node.querySelector('input').value = newValue;

    triggerEvent({
      node,
      selector: 'input',
      eventName: 'input'
    });

    expect(setValue.calledWith(newValue)).to.eql(true);
  });

  it('should not parse numbers for string variables', () => {
    update({variables, selectedIdx: 1, values: ['']});
    node.querySelector('input').value = '1234';

    triggerEvent({
      node,
      selector: 'input',
      eventName: 'input'
    });

    expect(setValue.calledWith(1234)).to.eql(false);
  });
});
