import {jsx} from 'view-utils';
import {mountTemplate, triggerEvent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {ValueSelection, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/variable/ValueSelection';

describe('<ValueSelection>', () => {
  let node;
  let update;
  let setValue;
  let variables;

  beforeEach(() => {
    variables = {
      data: [
        {values:['a', 'b', 'c']}
      ]
    };

    setValue = sinon.spy();
    __set__('setValue', setValue);

    ({node, update} = mountTemplate(<ValueSelection />));
  });

  afterEach(() => {
    __ResetDependency__('setValue');
  });

  it('should contain a list of values', () => {
    update({variables, selectedIdx: 0});

    expect(node.textContent).to.include('abc');
  });

  it('should have the current value checked', () => {
    update({variables, selectedIdx: 0, value: 'b'});

    const radios = node.querySelectorAll('input[type="radio"]');

    expect(radios[1].checked).to.eql(true);
  });

  it('should set the value when user clicks on button', () => {
    update({variables, selectedIdx: 0});

    const cRadio = node.querySelectorAll('input[type="radio"]')[2];

    triggerEvent({
      node: cRadio,
      eventName: 'click'
    });

    expect(setValue.calledWith('c')).to.eql(true);
  });
});
