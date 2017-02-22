import {expect} from 'chai';
import {enterGatewayAnalysisMode, setEndEvent, unsetEndEvent, setGateway, unsetGateway, leaveGatewayAnalysisMode,
        __set__, __ResetDependency__} from 'main/processDisplay/diagram/analytics/service';
import sinon from 'sinon';

describe('Analytics service', () => {
  const ENTER_GATEWAY_ANALYSIS_MODE = 'ENTER_GATEWAY_ANALYSIS_MODE';
  const SET_ELEMENT = 'SET_ELEMENT';

  let dispatchAction;
  let createEnterGatewayAnalysisModeAction;
  let createSetElementAction;

  beforeEach(() => {
    dispatchAction = sinon.spy();
    __set__('dispatchAction', dispatchAction);

    createEnterGatewayAnalysisModeAction = sinon.stub().returns(ENTER_GATEWAY_ANALYSIS_MODE);
    __set__('createEnterGatewayAnalysisModeAction', createEnterGatewayAnalysisModeAction);

    createSetElementAction = sinon.stub().returns(SET_ELEMENT);
    __set__('createSetElementAction', createSetElementAction);
  });

  afterEach(() => {
    __ResetDependency__('dispatchAction');
    __ResetDependency__('createEnterGatewayAnalysisModeAction');
    __ResetDependency__('createSetElementAction');
  });

  it('should enter gateway analysis mode', () => {
    enterGatewayAnalysisMode();

    expect(dispatchAction.calledWith(ENTER_GATEWAY_ANALYSIS_MODE)).to.eql(true);
  });

  it('should set the end event', () => {
    const element = {id: 'element'};

    setEndEvent(element);

    expect(dispatchAction.calledWith(SET_ELEMENT)).to.eql(true);
    expect(createSetElementAction.calledWith('element', 'endEvent')).to.eql(true);
  });

  it('should set the gateway', () => {
    const element = {id: 'element'};

    setGateway(element);

    expect(dispatchAction.calledWith(SET_ELEMENT)).to.eql(true);
    expect(createSetElementAction.calledWith('element', 'gateway')).to.eql(true);
  });

  it('should unset the end event', () => {
    unsetEndEvent();

    expect(dispatchAction.calledWith(SET_ELEMENT)).to.eql(true);
    expect(createSetElementAction.calledWith(null, 'endEvent')).to.eql(true);
  });

  it('should unset the gateway', () => {
    unsetGateway();

    expect(dispatchAction.calledWith(SET_ELEMENT)).to.eql(true);
    expect(createSetElementAction.calledWith(null, 'gateway')).to.eql(true);
  });

  it('should unset the gateway and end event when leaving the gateway analysis mode', () => {
    leaveGatewayAnalysisMode();

    expect(dispatchAction.calledTwice).to.eql(true);
    expect(createSetElementAction.calledWith(null, 'endEvent')).to.eql(true);
    expect(createSetElementAction.calledWith(null, 'gateway')).to.eql(true);
  });
});
