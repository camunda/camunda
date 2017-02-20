import {jsx, Socket, OnEvent, createReferenceComponent, createStateInjector, runUpdate} from 'view-utils';
import {createModal} from 'widgets';
import {clickElement} from './service';

export function createAnalyticsRenderer({viewer, node, eventsBus}) {
  const nodes = {};
  const Reference = createReferenceComponent(nodes);
  const Modal = createModal();
  const State = createStateInjector();

  const template = <State>
    <Modal>
      <Socket name="head">
        <button type="button" className="close">
          <OnEvent event='click' listener={Modal.close} />
          <span>×</span>
        </button>
        <h4 className="modal-title">
          <Reference name="name" />
        </h4>
      </Socket>
      <Socket name="body">
        <div>
          Process Instances that reached this state:
          <div style="text-align: center; font-size: 3rem; margin: 1.4rem;">
            <span>
              <Reference name="counter" />
            </span>
            %
          </div>
        </div>
        <h5>Actions</h5>
        <button type="button" className="btn btn-default">
          Perform Gateway Analysis
        </button>
      </Socket>
      <Socket name="foot">
        <button type="button" className="btn btn-default">
          <OnEvent event='click' listener={Modal.close} />
          Close
        </button>
      </Socket>
    </Modal>
  </State>;
  const templateUpdate = template(node, eventsBus);

  viewer.get('eventBus').on('element.click', ({element}) => {
    if (clickElement(element, State.getState(), nodes)) {
      Modal.open();
    }
  });

  return ({state, diagramRendered}) => {
    if (diagramRendered) {
      // set pointer style to cursor for end events
      viewer.get('elementRegistry').forEach((element, gfx) => {
        if (element.type === 'bpmn:EndEvent') {
          gfx.style.cursor = 'pointer';
        }
      });
    }

    runUpdate(templateUpdate, state);
  };
}
