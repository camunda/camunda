import {createMockComponent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {createDiagramControlsIntegrator, __set__, __ResetDependency__} from 'main/processDisplay/diagramControlsIntegrator';

describe('Diagram Controls Integrator', () => {
  let integrator;
  let Diagram;
  let Controls;
  let unsetGateway;
  let unsetEndEvent;
  let isBpmnType;
  let viewer;

  const diagramElement = 'SOME_ELEMENT';

  beforeEach(() => {
    viewer = {
      get: sinon.stub().returnsThis(),
      forEach: sinon.stub().callsArgWith(0, diagramElement),
      addMarker: sinon.spy(),
      removeMarker: sinon.spy()
    };

    const DiagramComponent = createMockComponent('Diagram');

    DiagramComponent.getViewer = sinon.stub().returns(viewer);

    const createDiagram = sinon.stub().returns(DiagramComponent);

    __set__('createDiagram', createDiagram);

    const ControlsComponent = createMockComponent('Controls');

    ControlsComponent.nodes = {
      Gateway: document.createElement('div'),
      EndEvent: document.createElement('div')
    };

    const createControls = sinon.stub().returns(ControlsComponent);

    __set__('createControls', createControls);

    unsetGateway = sinon.spy();
    __set__('unsetGateway', unsetGateway);

    unsetEndEvent = sinon.spy();
    __set__('unsetEndEvent', unsetEndEvent);

    isBpmnType = sinon.stub().returns(true);
    __set__('isBpmnType', isBpmnType);

    ({integrator, Diagram, Controls} = createDiagramControlsIntegrator());
  });

  afterEach(() => {
    __ResetDependency__('createDiagram');
    __ResetDependency__('createControls');
    __ResetDependency__('unsetGateway');
    __ResetDependency__('unsetEndEvent');
    __ResetDependency__('isBpmnType');
  });

  it('should return Diagram, Controls and the integrator', () => {
    expect(Diagram).to.exist;
    expect(Controls).to.exist;
    expect(integrator).to.exist;
  });

  describe('unset', () => {
    it('should clear the hover state for the type', () => {
      integrator.unset('Gateway');
      expect(viewer.removeMarker.calledWith(diagramElement)).to.eql(true);
    });
    it('should call the unset service function (Gateway)', () => {
      integrator.unset('Gateway');
      expect(unsetGateway.calledOnce).to.eql(true);
    });
    it('should call the unset service function (EndEvent)', () => {
      integrator.unset('EndEvent');
      expect(unsetEndEvent.calledOnce).to.eql(true);
    });
  });

  describe('update', () => {
    describe('with diagram update', () => {
      it('should add a hover marker to the diagram element when hovered', () => {
        integrator.update('Gateway', true, true);
        expect(viewer.addMarker.calledWith(diagramElement)).to.eql(true);
      });
      it('should remove the hover marker from the diagram element when not hovered', () => {
        integrator.update('Gateway', true, false);
        expect(viewer.removeMarker.calledWith(diagramElement)).to.eql(true);
      });
    });

    describe('without diagram update', () => {
      it('should not add a hover marker to the diagram element when hovered', () => {
        integrator.update('Gateway', false, true);
        expect(viewer.addMarker.calledWith(diagramElement)).to.eql(false);
      });
      it('should not remove the hover marker from the diagram element when not hovered', () => {
        integrator.update('Gateway', false, false);
        expect(viewer.removeMarker.calledWith(diagramElement)).to.eql(false);
      });
      it('should set the backgroundColor of the hovered input field', () => {
        integrator.update('Gateway', false, true);
        expect(Controls.nodes['Gateway'].style.backgroundColor).to.not.be.empty;
      });
      it('should not set the backgroundColor of the not hovered input field', () => {
        integrator.update('Gateway', false, true);
        expect(Controls.nodes['EndEvent'].style.backgroundColor).to.be.empty;
      });
    });
  });
});
