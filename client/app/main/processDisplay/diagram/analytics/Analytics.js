import {jsx, Socket, OnEvent, createReferenceComponent, DESTROY_EVENT, createStateComponent, runUpdate, $document} from 'view-utils';
import {createModal} from 'widgets';
import {enterGatewayAnalysisMode, setEndEvent, unsetEndEvent, setGateway, leaveGatewayAnalysisMode} from './service';
import {GATEWAY_ANALYSIS_MODE} from './reducer';
import {is} from 'utils';

export function createAnalyticsRenderer({viewer, node, eventsBus}) {
  const canvas = viewer.get('canvas');
  const elementRegistry = viewer.get('elementRegistry');

  const nodes = {};
  const Reference = createReferenceComponent(nodes);
  const Modal = createModal();
  const State = createStateComponent();

  const template = <State>
    <Modal onClose={unsetEndEvent}>
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
        <button type="button" className="btn btn-default startGatewayAnalysis">
          <OnEvent event="click" listener={startAnalyis} />
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

  function startAnalyis() {
    Modal.close({
      ignoreListeners: true
    });
    enterGatewayAnalysisMode();
  }

  function updateModalContent(element, data) {
    const instancesCount = getMax(data);

    nodes.name.textContent = element.name || element.id;
    nodes.counterAll.textContent = instancesCount || 0;
    nodes.counterReached.textContent = data[element.id] || 0;
    nodes.counterReachedPercentage.textContent = Math.round(data[element.id] / instancesCount * 1000) / 10 || 0;
  }

  viewer.get('eventBus').on('element.click', ({element}) => {
    const {mode, heatmap: {data}} = State.getState();

    if (mode === GATEWAY_ANALYSIS_MODE) {
      if (is(element, 'Gateway')) {
        setGateway(element);
      }
    } else if (is(element, 'EndEvent')) {
      setEndEvent(element);

      updateModalContent(element, data);
      Modal.open();
    }
  });

  const keydownListener = ({key}) => {
    const {mode, gateway} = State.getState();

    if (key === 'Escape' && mode === GATEWAY_ANALYSIS_MODE && !gateway) {
      leaveGatewayAnalysisMode();
    }
  };

  $document.addEventListener('keydown', keydownListener);
  eventsBus.on(DESTROY_EVENT, () => {
    $document.removeEventListener('keydown', keydownListener);
  });

  function needsHighlight(element, mode) {
    return mode === GATEWAY_ANALYSIS_MODE && is(element, 'Gateway') ||
           mode !== GATEWAY_ANALYSIS_MODE && is(element, 'EndEvent');
  }

  function removeHighlight(element) {
    canvas.removeMarker(element, 'highlight');
    canvas.removeMarker(element, 'highlight_selected');
  }

  function highlight(element, type = 'highlight') {
    if (element) {
      canvas.addMarker(element, type);
      const outline = elementRegistry.getGraphics(element).querySelector('.djs-outline');

      outline.setAttribute('rx', '14px');
      outline.setAttribute('ry', '14px');
    }
  }

  return ({state, diagramRendered}) => {
    if (diagramRendered) {
      elementRegistry.forEach((element) => {
        removeHighlight(element);
        if (needsHighlight(element, state.mode)) {
          highlight(element);
        }
      });

      Object.values(state.selection).forEach(element => {
        highlight(element, 'highlight_selected');
      });
    }

    runUpdate(templateUpdate, state);
  };
}

function getMax(allValues) {
  return Math.max.apply(null, Object.values(allValues));
}
