import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {View, __set__, __ResetDependency__} from 'main/processDisplay/controls/view/View';

describe('<View>', () => {
  let node;
  let update;
  let setView;
  let onNextTick;
  let onViewChanged;
  let Select;
  let onValueSelected;

  beforeEach(() => {
    setView = sinon.spy();
    __set__('setView', setView);

    onNextTick = sinon.stub().callsArg(0);
    __set__('onNextTick', onNextTick);

    Select = createMockComponent('Select');
    __set__('Select', Select);

    onViewChanged = sinon.spy();

    ({node, update} = mountTemplate(<View onViewChanged={onViewChanged} />));
    update({});

    onValueSelected = Select.getAttribute('onValueSelected');
  });

  afterEach(() => {
    __ResetDependency__('setView');
    __ResetDependency__('onNextTick');
  });

  it('should display a select field', () => {
    expect(node).to.contain.text(Select.text);
  });

  it('should set the view on selection', () => {
    onValueSelected({value: 'frequency'});

    expect(setView.calledWith('frequency')).to.eql(true);
  });

  it('should call onViewChanged on next update when view changes', () => {
    onValueSelected({value: 'frequency'});

    expect(onNextTick.calledWith(onViewChanged)).to.eql(true);
    expect(onViewChanged.calledOnce).to.eql(true);
  });
});
