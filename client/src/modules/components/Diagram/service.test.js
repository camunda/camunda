import {getElementType, getFlowNodesDetails} from './service';

import {FLOW_NODE_TYPE, UNNAMED_ACTIVITY} from 'modules/constants';

const someTask = {
  id: 'someTask',
  businessObject: {
    name: 'some task',
    $instanceOf: type => type === 'bpmn:Task'
  }
};
const someStartEvent = {
  id: 'someStartEvent',
  businessObject: {
    name: 'some start event',
    $instanceOf: type => type === 'bpmn:StartEvent'
  }
};
const someEndEvent = {
  id: 'someEndEvent',
  businessObject: {
    name: 'some end event',
    $instanceOf: type => type === 'bpmn:EndEvent'
  }
};
const someGateway = {
  id: 'someGateway',
  businessObject: {
    $instanceOf: type => type === 'bpmn:Gateway'
  }
};
const someNoNamed = {
  id: 'someNoNamed',
  businessObject: {
    $instanceOf: () => null
  }
};

describe('Diagram service', () => {
  describe('getElementType', () => {
    it('should return null if type is label', () => {
      expect(getElementType({businessObject: {}, type: 'label'})).toBe(null);
    });

    it('should type base on businessObject', () => {
      expect(getElementType(someTask)).toBe(FLOW_NODE_TYPE.TASK);
      expect(getElementType(someStartEvent)).toBe(FLOW_NODE_TYPE.START_EVENT);
      expect(getElementType(someEndEvent)).toBe(FLOW_NODE_TYPE.END_EVENT);
      expect(getElementType(someGateway)).toBe(FLOW_NODE_TYPE.GATEWAY);
    });
  });

  describe('getActivitiesInfoMap', () => {
    // given
    const elementRegistry = [
      someTask,
      someNoNamed,
      someStartEvent,
      someGateway
    ];
    const expectedFlowNodesDetails = {
      someTask: {
        name: someTask.businessObject.name,
        type: getElementType(someTask)
      },
      someStartEvent: {
        name: someStartEvent.businessObject.name,
        type: getElementType(someStartEvent)
      },
      someGateway: {name: UNNAMED_ACTIVITY, type: getElementType(someGateway)}
    };

    // then
    expect(getFlowNodesDetails(elementRegistry)).toEqual(
      expectedFlowNodesDetails
    );
  });
});
