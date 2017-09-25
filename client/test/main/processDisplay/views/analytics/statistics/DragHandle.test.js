import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import sinon from 'sinon';
import {DragHandle, __set__, __ResetDependency__} from 'main/processDisplay/views/analytics/statistics/DragHandle';
import React from 'react';
import {mount} from 'enzyme';
import {triggerEvent} from 'testHelpers';

chai.use(chaiEnzyme());

const {expect} = chai;
const jsx = React.createElement;

describe('<DragHandle>', () => {
  let setHeight;
  let node;

  beforeEach(() => {
    setHeight = sinon.spy();
    __set__('setHeight', setHeight);

    node = mount(<DragHandle height={100} />);
  });

  afterEach(() => {
    __ResetDependency__('setHeight');
  });

  it('should add a node to the DOM', () => {
    expect(node).to.containMatchingElement(<div />);
  });

  it('should set the height when dragging the handle', () => {
    node.simulate('mousedown', {screenY: 50});

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
