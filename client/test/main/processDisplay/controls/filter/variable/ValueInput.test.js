import {jsx} from 'view-utils';
import {mountTemplate, triggerEvent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {ValueInput, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/variable/ValueInput';

describe('<ValueInput>', () => {
  let node;
  let update;
  let setValue;
  let variables;

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

    ({node, update} = mountTemplate(<ValueInput />));
  });

  afterEach(() => {
    __ResetDependency__('setValue');
  });

  it('should contain an input field', () => {
    update({variables});
    expect(node.querySelector('input')).to.exist;
  });

  it('should have a number typed input field for numbers', () => {
    update({
      variables,
      selectedIdx: 2
    });

    expect(node.querySelector('input').getAttribute('type')).to.eql('number');
  });

  it('should have a text input field by default', () => {
    update({
      variables,
      selectedIdx: 1
    });

    expect(node.querySelector('input').getAttribute('type')).to.eql('text');
  });

  it('should have a hidden input field for booleans', () => {
    update({
      variables,
      selectedIdx: 0
    });

    expect(node.querySelector('input').getAttribute('type')).to.eql('hidden');
  });

  it('should have a hidden input field if no variable is selected', () => {
    update({variables});

    expect(node.querySelector('input').getAttribute('type')).to.eql('hidden');
  });

  it('should reflect the state value in the input field', () => {
    const value = 'AAAAA';

    update({variables, value});

    expect(node.querySelector('input').value).to.eql(value);
  });

  it('should set the value if the user changes it', () => {
    const newValue = 'newValue';

    update({variables, selectedIdx: 1});
    node.querySelector('input').value = newValue;

    triggerEvent({
      node,
      selector: 'input',
      eventName: 'input'
    });

    expect(setValue.calledWith(newValue)).to.eql(true);
  });

  it('should parse numbers for numeric variables', () => {
    update({variables, selectedIdx: 2});
    node.querySelector('input').value = '1234';

    triggerEvent({
      node,
      selector: 'input',
      eventName: 'input'
    });

    expect(setValue.calledWith(1234)).to.eql(true);
  });

  it('should not parse numbers for string variables', () => {
    update({variables, selectedIdx: 1});
    node.querySelector('input').value = '1234';

    triggerEvent({
      node,
      selector: 'input',
      eventName: 'input'
    });

    expect(setValue.calledWith(1234)).to.eql(false);
  });
});
