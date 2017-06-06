import {expect} from 'chai';
import sinon from 'sinon';
import {setupPromiseMocking, triggerEvent} from 'testHelpers';
import {setTargetValue, getTargetValue, getTargetDurationFromForm, setTargetDurationToForm, saveTargetValues, addTargetValueBadge, addTargetValueTooltip, prepareFlowNodes,
        __set__, __ResetDependency__} from 'main/processDisplay/views/targetValueDisplay/service';

describe('TargetValue service', () => {
  setupPromiseMocking();

  describe('setTargetValue', () => {
    let dispatchAction;
    let createSetTargetValueAction;
    const ACTION = 'ACTION';
    const ELEMENT = 'ELEMENT';
    const VALUE = 'VALUE';

    beforeEach(() => {
      dispatchAction = sinon.spy();
      createSetTargetValueAction = sinon.stub().returns(ACTION);

      __set__('dispatchAction', dispatchAction);
      __set__('createSetTargetValueAction', createSetTargetValueAction);

      setTargetValue(ELEMENT, VALUE);
    });

    afterEach(() => {
      __ResetDependency__('dispatchAction');
      __ResetDependency__('createSetTargetValueAction');
    });

    it('should create an action', () => {
      expect(createSetTargetValueAction.calledWith(ELEMENT, VALUE)).to.eql(true);
    });

    it('should dispatch that action', () => {
      expect(dispatchAction.calledWith(ACTION)).to.eql(true);
    });
  });

  describe('getTargetValue', () => {
    const ELEMENT_ID = 'ELEMENT_ID';
    const TARGET_VALUE = 400;

    it('should return target value from State component', () => {
      const element = {
        businessObject: {
          id: ELEMENT_ID
        }
      };
      const State = {
        getState: sinon.stub().returns({
          targetValue: {
            data: {
              ELEMENT_ID: TARGET_VALUE
            }
          }
        })
      };

      expect(getTargetValue(State, element)).to.eql(TARGET_VALUE);
    });
  });

  describe('getTargetDurationFromForm', () => {
    it('should parse a duration form', () => {
      const form = document.createElement('form');

      form.innerHTML = `<input for="ms" value="0" />
        <input for="s" value="0" />
        <input for="m" value="0" />
        <input for="h" value="0" />
        <input for="d" value="1" />
        <input for="w" value="0" />`;

      expect(getTargetDurationFromForm(form)).to.eql(864e5);
    });
  });

  describe('setTargetDurationToForm', () => {
    it('should set values to form', () => {
      const form = document.createElement('form');

      form.innerHTML = '<input for="s" />';

      setTargetDurationToForm(form, 4000);
      expect(form.querySelector('input').value).to.eql('4');
    });
  });

  describe('saveTargetValues', () => {
    let put;
    let addNotification;
    const PROCESS_ID = 'PROCESS_ID';
    const TARGET_VALUES = 'TARGET_VALUES';

    beforeEach(() => {
      put = sinon.stub();
      __set__('put', put);

      addNotification = sinon.spy();
      __set__('addNotification', addNotification);
    });

    afterEach(() => {
      __ResetDependency__('put');
      __ResetDependency__('addNotification');
    });

    it('should call the backend', () => {
      put.returns(Promise.resolve());

      saveTargetValues(PROCESS_ID, TARGET_VALUES);

      expect(put.calledOnce).to.eql(true);
      expect(put.firstCall.args[1].processDefinitionId).to.eql(PROCESS_ID);
      expect(put.firstCall.args[1].targetValues).to.eql(TARGET_VALUES);
    });

    it('should show error message if something goes wrong', () => {
      put.returns(Promise.reject());

      saveTargetValues(PROCESS_ID, TARGET_VALUES);

      Promise.runAll();

      expect(addNotification.calledOnce).to.eql(true);
      expect(addNotification.firstCall.args[0].isError).to.eql(true);
    });
  });

  describe('prepareFlowNodes', () => {
    it('should return processed data', () => {
      const targetValues = {
        a: 2,
        b: 8,
        c: 3
      };
      const actualValues = {
        a: 1,
        b: 12,
        c: 9
      };

      const processed = prepareFlowNodes(targetValues, actualValues);

      expect(processed.a).to.not.exist; // below target value --> skip
      expect(processed.b).to.eql(0.5);  // it's 50% above the target value
      expect(processed.c).to.eql(2);    // actual value is 200% above times target value
    });
  });

  describe('addTargetValueBadge', () => {
    let viewer;
    let addFunction;
    let element;
    let onClick;
    const ELEMENT_ID = 'ELEMENT_ID';
    const VALUE = 400;

    beforeEach(() => {
      addFunction = sinon.spy();
      onClick = sinon.spy();

      element = {
        id: ELEMENT_ID
      };

      viewer = {
        get: sinon.stub().returns({
          add: addFunction
        })
      };

      addTargetValueBadge(viewer, element, VALUE, onClick);
    });

    it('should add an overlay', () => {
      expect(addFunction.calledWith(ELEMENT_ID)).to.eql(true);
    });

    it('should call the callback when overlay is clicked', () => {
      const overlayHtml = addFunction.firstCall.args[2].html;

      triggerEvent({
        node: overlayHtml,
        eventName: 'click'
      });

      expect(onClick.calledWith(element)).to.eql(true);
    });
  });

  describe('addTargetValueTooltip', () => {
    let viewer;
    let addFunction;
    let overlayHtml;
    let diagramGraphics;
    let addDiagramTooltip;

    const TARGET_VALUE = 400;
    const ACTUAL_VALUE = 100;
    let element;

    beforeEach(() => {
      element = {id: 'element'};

      addFunction = sinon.spy();

      diagramGraphics = document.createElement('div');
      diagramGraphics.innerHTML = '<div class="djs-hit" width="20"></div>';

      addDiagramTooltip = sinon.spy();
      __set__('addDiagramTooltip', addDiagramTooltip);

      viewer = {
        get: sinon.stub().returns({
          add: addFunction,
          getGraphics: sinon.stub().returns(diagramGraphics)
        })
      };

      addTargetValueTooltip(viewer, element, ACTUAL_VALUE, TARGET_VALUE);

      overlayHtml = addDiagramTooltip.firstCall.args[2];
    });

    afterEach(() => {
      __ResetDependency__('addDiagramTooltip');
    });

    it('should add an tooltip', () => {
      expect(addDiagramTooltip.calledWith(viewer, element.id)).to.eql(true);
    });

    it('should contain the target value in the overlay', () => {
      expect(overlayHtml.textContent).to.contain('400ms');
    });

    it('should contain the actual value in the overlay', () => {
      expect(overlayHtml.textContent).to.contain('100ms');
    });

    it('should contain the percentage in the overlay', () => {
      expect(overlayHtml.textContent).to.contain('75%');
    });

    it('should show "no data" when no actual value exists', () => {
      addTargetValueTooltip(viewer, element.id, undefined, TARGET_VALUE);

      expect(addDiagramTooltip.lastCall.args[2].textContent).to.contain('actual:\xa0no\xa0data');
    });
  });
});
