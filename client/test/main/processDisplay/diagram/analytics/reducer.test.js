import {expect} from 'chai';
import {reducer, createSetElementAction,
        __set__, __ResetDependency__} from 'main/processDisplay/diagram/analytics/reducer';

describe('Analytics reducer', () => {
  const elementType = 'gateway';
  const elementId = 'gateway8';
  const SELECT_PROCESS_DEFINITION = 'SELECT_PROCESS_DEFINITION';
  const SET_VIEW = 'SET_VIEW';

  beforeEach(() => {
    __set__('SELECT_PROCESS_DEFINITION', SELECT_PROCESS_DEFINITION);
  });

  afterEach(() => {
    __ResetDependency__('SELECT_PROCESS_DEFINITION');
  });

  it('should set the element id', () => {
    const {selection: {gateway}} = reducer(undefined, createSetElementAction(elementId, elementType));

    expect(gateway).to.eql(elementId);
  });

  it('should unset selected elements when the process definition is switched', () => {
    const {selection: {gateway, endEvent}} = reducer({
      gateway: 'g1',
      endEvent: 'e2'
    }, {type: SELECT_PROCESS_DEFINITION});

    expect(gateway).to.be.undefined;
    expect(endEvent).to.be.undefined;
  });

  it('should unset selected elements when the view is switched', () => {
    const {selection: {gateway, endEvent}} = reducer({
      gateway: 'g1',
      endEvent: 'e2'
    }, {type: SET_VIEW});

    expect(gateway).to.be.undefined;
    expect(endEvent).to.be.undefined;
  });
});
