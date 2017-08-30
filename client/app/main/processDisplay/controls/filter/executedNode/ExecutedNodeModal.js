import {jsx, Socket, OnEvent, createStateComponent} from 'view-utils';
import {createModal} from 'widgets';
import {onNextTick} from 'utils';
import {createSelectedNodeDiagram} from './SelectNodeDiagram';
import {addFlowNodesFilter} from './service';

export function createExecutedNodeModal(onFilterAdded, getDiagramXML) {
  const Modal = createModal();
  const SelectNodeDiagram = createSelectedNodeDiagram();
  const State = createStateComponent();
  let currentlySelected = [];

  const ExecutedNodeModal = () => {
    return <State>
      <Modal size="modal-lg" onOpen={onOpen}>
        <Socket name="head">
          <button type="button" className="close">
            <OnEvent event="click" listener={Modal.close} />
            <span>×</span>
          </button>
          <h4 className="modal-title">New Executed Node Filter</h4>
        </Socket>
        <Socket name="body">
          <SelectNodeDiagram onSelectionChange={onSelectionChange} />
        </Socket>
        <Socket name="foot">
          <button type="button" className="btn btn-default">
            <OnEvent event="click" listener={abort} />
            Abort
          </button>
          <button type="button" className="btn btn-primary">
            <OnEvent event="click" listener={createFilter} />
            Create Filter
          </button>
        </Socket>
      </Modal>
    </State>;

    function onOpen() {
      SelectNodeDiagram.loadDiagram(getDiagramXML(), currentlySelected);
    }

    function onSelectionChange(selected) {
      currentlySelected = selected;
    }

    function createFilter() {
      addFlowNodesFilter(currentlySelected);

      Modal.close();
      onNextTick(onFilterAdded);
    }

    function abort() {
      Modal.close();
    }
  };

  ExecutedNodeModal.open = () => {
    Modal.open();
  };

  return ExecutedNodeModal;
}
