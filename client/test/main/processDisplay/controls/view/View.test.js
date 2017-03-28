import {jsx} from 'view-utils';
import {mountTemplate, triggerEvent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {View, __set__, __ResetDependency__} from 'main/processDisplay/controls/view/View';

describe('<View>', () => {
  let node;
  let update;
  let setView;
  let onNextUpdate;
  let onViewChanged;

  beforeEach(() => {
    setView = sinon.spy();
    __set__('setView', setView);

    onNextUpdate = sinon.stub().callsArg(0);
    __set__('onNextUpdate', onNextUpdate);

    onViewChanged = sinon.spy();

    ({node, update} = mountTemplate(<View onViewChanged={onViewChanged} />));
    update({});
  });

  afterEach(() => {
    __ResetDependency__('setView');
    __ResetDependency__('onNextUpdate');
  });

  it('should display a select field', () => {
    expect(node.querySelector('select')).to.exist;
  });

  it('should set the view on selection', () => {
    node.querySelector('select').value = 'frequency';
    triggerEvent({
      node,
      selector: 'select',
      eventName: 'change'
    });

    expect(setView.calledWith('frequency')).to.eql(true);
  });

  it('should call onViewChanged on next update when view changes', () => {
    node.querySelector('select').value = 'frequency';
    triggerEvent({
      node,
      selector: 'select',
      eventName: 'change'
    });

    expect(onNextUpdate.calledWith(onViewChanged)).to.eql(true);
    expect(onViewChanged.calledOnce).to.eql(true);
  });
});
