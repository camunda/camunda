import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {View, __set__, __ResetDependency__} from 'main/processDisplay/controls/view/View';

describe('<View>', () => {
  let node;
  let update;
  let onNextTick;
  let onViewChanged;
  let Select;
  let onValueSelected;
  let Link;

  beforeEach(() => {
    onNextTick = sinon.stub().callsArg(0);
    __set__('onNextTick', onNextTick);

    Select = createMockComponent('Select', true);
    __set__('Select', Select);

    Link = createMockComponent('Link');
    __set__('Link', Link);

    onViewChanged = sinon.spy();

    ({node, update} = mountTemplate(<View onViewChanged={onViewChanged} />));
    update({});

    onValueSelected = Select.getAttribute('onValueSelected');
  });

  afterEach(() => {
    __ResetDependency__('onNextTick');
    __ResetDependency__('Select');
    __ResetDependency__('Link');
  });

  it('should display a select field', () => {
    expect(node).to.contain.text(Select.text);
  });

  it('should call onViewChanged on next update when view changes', () => {
    onValueSelected({value: 'frequency'});

    expect(onViewChanged.calledWith('frequency')).to.eql(true);
  });

  it('should contains a targetValueComparison option', () => {
    expect(node.textContent).to.contain('Target Value Comparison');
  });
});
