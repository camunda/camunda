import {jsx} from 'view-utils';
import {mountTemplate, triggerEvent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {View, __set__, __ResetDependency__} from 'main/processDisplay/controls/view/View';

describe('<View>', () => {
  let node;
  let update;
  let setView;

  beforeEach(() => {
    setView = sinon.spy();
    __set__('setView', setView);

    ({node, update} = mountTemplate(<View />));
    update({});
  });

  afterEach(() => {
    __ResetDependency__('setView');
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
});
