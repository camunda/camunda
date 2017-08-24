import {jsx} from 'view-utils';
import {mountTemplate, triggerEvent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {ExecutedNodeFilter} from 'main/processDisplay/controls/filter/executedNode/ExecutedNodeFilter';

describe('main/processDisplay/controls/filter/executedNode <ExecutedNodeFilter>', () => {
  let onDelete;
  let node;
  let update;

  beforeEach(() => {
    onDelete = sinon.spy();

    ({node, update} = mountTemplate(<ExecutedNodeFilter onDelete={onDelete} />));
  });

  it('should render nodes names and ids', () => {
    const state = [
      {
        name: 'alina'
      },
      {
        id: 'kot1'
      }
    ];

    update(state);

    expect(node).to.contain.text('alina');
    expect(node).to.contain.text('kot1');
  });

  it('should call onDelete when button is clicked', () => {
    expect(onDelete.called).to.eql(false);

    triggerEvent({
      node,
      selector: 'button',
      eventName: 'click'
    });

    expect(onDelete.calledOnce).to.eql(true);
  });
});
