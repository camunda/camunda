import {expect} from 'chai';
import {
  reducer, createUnsetElementAction, createToggleElementAction,
  createAddHighlightAction, createRemoveHighlightsAction
} from 'main/processDisplay/diagram/analytics/reducer';

describe('Analytics reducer', () => {
  const elementType = 'gateway';
  const elementId = 'gateway8';
  const CHANGE_ROUTE_ACTION = 'CHANGE_ROUTE_ACTION';
  let selectedGatewayState;

  beforeEach(() => {
    selectedGatewayState = {
      selection: {
        gateway: elementId
      }
    };
  });

  it('should be possible to add highlight', () => {
    const {hover: {[elementType]: entry}} = reducer(
      undefined,
      createAddHighlightAction(elementType, elementId)
    );

    expect(entry).to.eql({elementType, elementId});
  });

  it('should be possible to remove highlights', () => {
    const highlightsState = {
      hover: {
        a: 1
      }
    };

    const {hover} = reducer(
      highlightsState,
      createRemoveHighlightsAction()
    );

    expect(hover).to.eql({});
  });

  it('should set the element id on toggle if nothing was set previously', () => {
    const {selection: {gateway}} = reducer(undefined, createToggleElementAction(elementId, elementType));

    expect(gateway).to.eql(elementId);
  });

  it('should unset the element id on toggle if element was set previously', () => {
    const {selection: {gateway}}  = reducer(selectedGatewayState, createToggleElementAction(elementId, elementType));

    expect(gateway).to.be.undefined;
  });

  it('should replace a selected element', () => {
    const elementId = 'd23';
    const {selection: {gateway}}  = reducer(selectedGatewayState, createToggleElementAction(elementId, elementType));

    expect(gateway).to.eql(elementId);
  });

  it('should unset an element', () => {
    const {selection: {gateway}}  = reducer(selectedGatewayState, createUnsetElementAction(elementType));

    expect(gateway).to.be.undefined;
  });

  it('should reset state when the view is switched', () => {
    const {selection, hover} = reducer(
      {
        selection: {
          gateway: 'g1',
          endEvent: 'e2'
        },
        hover: {
          dd: 11
        }
      },
      {
        type: CHANGE_ROUTE_ACTION,
        route: {
          params: {
            view: 'something-else'
          }
        }
      }
    );

    expect(selection).to.be.eql({});
    expect(hover).to.be.eql({});
  });
});
