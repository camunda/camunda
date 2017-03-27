import {expect} from 'chai';
import {reducer, createSetElementAction} from 'main/processDisplay/diagram/analytics/reducer';

describe('Analytics reducer', () => {
  const elementType = 'gateway';
  const elementId = 'gateway8';
  const SET_VIEW = 'SET_VIEW';

  it('should set the element id', () => {
    const {selection: {gateway}} = reducer(undefined, createSetElementAction(elementId, elementType));

    expect(gateway).to.eql(elementId);
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
