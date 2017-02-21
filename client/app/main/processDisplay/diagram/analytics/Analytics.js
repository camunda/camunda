import {jsx, Socket, OnEvent, createReferenceComponent, createStateComponent, runUpdate} from 'view-utils';
import {createModal} from 'widgets';

export function createAnalyticsRenderer({viewer, node, eventsBus}) {
  const nodes = {};
  const Reference = createReferenceComponent(nodes);
  const Modal = createModal();
  const State = createStateComponent();

  const template = <State>
    <Modal>
      <Socket name="head">
        <button type="button" className="close">
          <OnEvent event="click" listener={Modal.close} />
          <span>×</span>
        </button>
        <h4 className="modal-title">
          <Reference name="name" />
        </h4>
      </Socket>
      <Socket name="body">
        <table className="cam-table end-event-statistics">
          <tbody>
            <tr>
              <td><Reference name="counterAll" /></td>
              <td>Process Instances Total</td>
            </tr>
            <tr>
              <td><Reference name="counterReached" /></td>
              <td>Process Instances reached this state</td>
            </tr>
            <tr>
              <td><span><Reference name="counterReachedPercentage" /></span>%</td>
              <td>of Process Instances reached this state</td>
            </tr>
          </tbody>
        </table>
        <h5>Actions</h5>
        <button type="button" className="btn btn-default">
          Perform Gateway Analysis
        </button>
      </Socket>
      <Socket name="foot">
        <button type="button" className="btn btn-default">
          <OnEvent event="click" listener={Modal.close} />
          Close
        </button>
      </Socket>
    </Modal>
  </State>;
  const templateUpdate = template(node, eventsBus);

  viewer.get('eventBus').on('element.click', ({element: {type, name, id}}) => {
    if (type === 'bpmn:EndEvent') {
      const {heatmap: {data}} = State.getState();
      const instancesCount = getMax(data);

      nodes.name.textContent = name || id;
      nodes.counterAll.textContent = instancesCount;
      nodes.counterReached.textContent = data[id];
      nodes.counterReachedPercentage.textContent = Math.round(data[id] / instancesCount * 1000) / 10;
      Modal.open();
    }
  });

  return ({state, diagramRendered}) => {
    if (diagramRendered) {
      // highlight end events
      const canvas = viewer.get('canvas');

      viewer.get('elementRegistry').forEach((element, gfx) => {
        if (element.type === 'bpmn:EndEvent') {
          canvas.addMarker(element.id, 'highlight');
          const outline = gfx.querySelector('.djs-outline');

          outline.setAttribute('rx', '14px');
          outline.setAttribute('ry', '14px');
        }
      });
    }

    runUpdate(templateUpdate, state);
  };
}

function getMax(allValues) {
  return Math.max.apply(null, Object.values(allValues));
}
