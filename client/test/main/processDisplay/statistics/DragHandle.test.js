import {jsx} from 'view-utils';
import {mountTemplate, triggerEvent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {DragHandle, __set__, __ResetDependency__} from 'main/processDisplay/statistics/DragHandle';

describe('<DragHandle>', () => {
  let setHeight;
  let node;
  let update;

  beforeEach(() => {
    setHeight = sinon.spy();
    __set__('setHeight', setHeight);

    ({node, update} = mountTemplate(<div style="height: 100px;"><DragHandle /></div>));
    update();
  });

  afterEach(() => {
    __ResetDependency__('setHeight');
  });

  it('should add a node to the DOM', () => {
    expect(node.querySelector('.drag-handle')).to.exist;
  });

  it('should set the height when dragging the handle', () => {
    triggerEvent({
      node,
      selector: '.drag-handle',
      eventName: 'mousedown',
      properties: {
        screenY: 50
      }
    });

    triggerEvent({
      node: document,
      eventName: 'mousemove',
      properties: {
        screenY: 40
      }
    });

    expect(setHeight.calledWith(110)).to.eql(true);

    triggerEvent({
      node: document,
      eventName: 'mouseup',
      properties: {
        screenY: 30
      }
    });

    expect(setHeight.calledWith(120)).to.eql(true);
  });
});
