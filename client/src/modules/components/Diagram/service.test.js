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
const someEvent = {
  id: 'someEvent',
  businessObject: {
    name: 'some event',
    $instanceOf: type => type === 'bpmn:Event'
  }
};
const someExclusiveGateway = {
  id: 'someExclusiveGateway',
  businessObject: {
    $instanceOf: type => type === 'bpmn:ExclusiveGateway'
  }
};
const someParallelGateway = {
  id: 'someParallelGateway',
  businessObject: {
    $instanceOf: type => type === 'bpmn:ParallelGateway'
  }
};
const someNoNamed = {
  id: 'someNoNamed',
  businessObject: {
    $instanceOf: () => null
  }
};

describe.skip('Diagram service', () => {
  describe('getElementType', () => {
    it('should return null if type is label', () => {
      expect(getElementType({businessObject: {}, type: 'label'})).toBe(null);
    });

    it('should type base on businessObject', () => {
      expect(getElementType(someTask)).toBe(FLOW_NODE_TYPE.TASK);
      expect(getElementType(someEvent)).toBe(FLOW_NODE_TYPE.EVENT);
      expect(getElementType(someStartEvent)).toBe(FLOW_NODE_TYPE.START_EVENT);
      expect(getElementType(someEndEvent)).toBe(FLOW_NODE_TYPE.END_EVENT);
      expect(getElementType(someExclusiveGateway)).toBe(
        FLOW_NODE_TYPE.EXCLUSIVE_GATEWAY
      );
      expect(getElementType(someParallelGateway)).toBe(
        FLOW_NODE_TYPE.PARALLEL_GATEWAY
      );
    });
  });

  describe('getActivitiesInfoMap', () => {
    // given
    const elementRegistry = [
      someTask,
      someNoNamed,
      someEvent,
      someStartEvent,
      someExclusiveGateway,
      someParallelGateway
    ];
    const expectedFlowNodesDetails = {
      someTask: {
        name: someTask.businessObject.name,
        type: getElementType(someTask)
      },
      someEvent: {
        name: someEvent.businessObject.name,
        type: getElementType(someEvent)
      },
      someStartEvent: {
        name: someStartEvent.businessObject.name,
        type: getElementType(someStartEvent)
      },
      someExclusiveGateway: {
        name: UNNAMED_ACTIVITY,
        type: getElementType(someExclusiveGateway)
      },
      someParallelGateway: {
        name: UNNAMED_ACTIVITY,
        type: getElementType(someParallelGateway)
      }
    };

    // then
    expect(getFlowNodesDetails(elementRegistry)).toEqual(
      expectedFlowNodesDetails
    );
  });
});
