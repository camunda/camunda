import {expect} from 'chai';
import {reducer, createEnterGatewayAnalysisModeAction, createSetElementAction, GATEWAY_ANALYSIS_MODE,
        __set__, __ResetDependency__} from 'main/processDisplay/diagram/analytics/reducer';

describe('Analytics reducer', () => {
  const elementType = 'gateway';
  const elementId = 'gateway8';
  const SELECT_PROCESS_DEFINITION = 'SELECT_PROCESS_DEFINITION';

  beforeEach(() => {
    __set__('SELECT_PROCESS_DEFINITION', SELECT_PROCESS_DEFINITION);
  });

  afterEach(() => {
    __ResetDependency__('SELECT_PROCESS_DEFINITION');
  });

  it('should set the mode to gateway analysis', () => {
    const {mode} = reducer(undefined, createEnterGatewayAnalysisModeAction());

    expect(mode).to.eql(GATEWAY_ANALYSIS_MODE);
  });

  it('should set the element id', () => {
    const {selection: {gateway}} = reducer(undefined, createSetElementAction(elementId, elementType));

    expect(gateway).to.eql(elementId);
  });

  it('should leave the mode when no element id is set', () => {
    const {mode} = reducer({mode: GATEWAY_ANALYSIS_MODE}, createSetElementAction(null, elementType));

    expect(mode).to.be.null;
  });

  it('should leave the mode and unset selected elements when the process definition is switched', () => {
    const {mode, selection: {gateway, endEvent}} = reducer({
      mode: GATEWAY_ANALYSIS_MODE,
      gateway: 'g1',
      endEvent: 'e2'
    }, {type: SELECT_PROCESS_DEFINITION});

    expect(mode).to.be.null;
    expect(gateway).to.be.undefined;
    expect(endEvent).to.be.undefined;
  });
});
