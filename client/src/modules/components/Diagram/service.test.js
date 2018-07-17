import {
  getDiagramColors,
  getElementType,
  getActivitiesInfoMap
} from './service';

import {ACTIVITY_TYPE, UNNAMED_ACTIVITY} from 'modules/constants';

const someTask = {
  id: 'someTask',
  businessObject: {
    name: 'some task',
    $instanceOf: type => type === 'bpmn:Task'
  }
};
const someEvent = {
  id: 'someEvent',
  businessObject: {
    name: 'some event',
    $instanceOf: type => type === 'bpmn:Event'
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
      expect(getElementType(someTask)).toBe(ACTIVITY_TYPE.TASK);
      expect(getElementType(someEvent)).toBe(ACTIVITY_TYPE.EVENT);
      expect(getElementType(someGateway)).toBe(ACTIVITY_TYPE.GATEWAY);
    });
  });

  describe('getActivitiesInfoMap', () => {
    // given
    const elementRegistry = [someTask, someNoNamed, someEvent, someGateway];
    const expectedActivitiesInfoMap = {
      someTask: {
        name: someTask.businessObject.name,
        type: getElementType(someTask)
      },
      someEvent: {
        name: someEvent.businessObject.name,
        type: getElementType(someEvent)
      },
      someGateway: {name: UNNAMED_ACTIVITY, type: getElementType(someGateway)}
    };

    // then
    expect(getActivitiesInfoMap(elementRegistry)).toEqual(
      expectedActivitiesInfoMap
    );
  });
});
