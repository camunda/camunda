import {expect} from 'chai';
import {reducer, createSetElementAction} from 'main/processDisplay/diagram/analytics/reducer';

describe('Analytics reducer', () => {
  const elementType = 'gateway';
  const elementId = 'gateway8';
  const CHANGE_ROUTE_ACTION = 'CHANGE_ROUTE_ACTION';

  it('should set the element id', () => {
    const {selection: {gateway}} = reducer(undefined, createSetElementAction(elementId, elementType));

    expect(gateway).to.eql(elementId);
  });

  it('should unset selected elements when the view is switched', () => {
    const {selection: {gateway, endEvent}} = reducer(
      {
        gateway: 'g1',
        endEvent: 'e2'
      },
      {
        type: CHANGE_ROUTE_ACTION,
        route: {
          params: {
            view: 'something-else'
          }
        }
      });

    expect(gateway).to.be.undefined;
    expect(endEvent).to.be.undefined;
  });
});
