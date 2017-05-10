import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {expect} from 'chai';
import {OperatorSelection, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/variable/OperatorSelection';

describe('<OperatorSelection>', () => {
  let node;
  let update;
  let OperatorButton;
  let variables;

  beforeEach(() => {
    variables = {
      data: [
        {type: 'Boolean'},
        {type: 'String'},
        {type: 'Number'}
      ]
    };

    OperatorButton = createMockComponent('OperatorButton');
    __set__('OperatorButton', OperatorButton);

    ({node, update} = mountTemplate(<OperatorSelection />));
  });

  afterEach(() => {
    __ResetDependency__('OperatorButton');
  });

  it('should not display any operators if no variable is selected', () => {
    update({
      variables,
      selectedIdx: undefined
    });

    expect(node.textContent).to.not.contain(OperatorButton.text);
  });

  it('should have equals and non equals buttons for strings', () => {
    update({
      variables,
      selectedIdx: 1
    });

    expect(node.textContent).to.contain(OperatorButton.text);
    expect(OperatorButton.appliedWith({operator: '='})).to.eql(true);
    expect(OperatorButton.appliedWith({operator: '!='})).to.eql(true);
  });

  it('should have buttons with implicit values for booleans', () => {
    update({
      variables,
      selectedIdx: 0
    });

    expect(node.textContent).to.contain(OperatorButton.text);
    expect(OperatorButton.appliedWith({operator: '='})).to.eql(true);
    expect(OperatorButton.appliedWith({implicitValue: 'true'})).to.eql(true);
    expect(OperatorButton.appliedWith({operator: '='})).to.eql(true);
    expect(OperatorButton.appliedWith({implicitValue: 'false'})).to.eql(true);
  });

  it('should have =, !=, < and > operators for all other types (numbers and dates)', () => {
    update({
      variables,
      selectedIdx: 2
    });

    expect(node.textContent).to.contain(OperatorButton.text);
    expect(OperatorButton.appliedWith({operator: '='})).to.eql(true);
    expect(OperatorButton.appliedWith({operator: '!='})).to.eql(true);
    expect(OperatorButton.appliedWith({operator: '>'})).to.eql(true);
    expect(OperatorButton.appliedWith({operator: '<'})).to.eql(true);
  });
});
