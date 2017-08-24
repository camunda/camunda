import {jsx, Socket} from 'view-utils';
import {Dropdown, DropdownItem} from 'widgets';
import {createDateModal} from './date';
import {createVariableModal} from './variable';
import {createExecutedNodeModal} from './executedNode';

export function CreateFilter({onFilterAdded, getProcessDefinition, getDiagramXML}) {
  return (parentNode, eventsBus) => {
    const DateModal = createDateModal(onFilterAdded);
    const VariableModal = createVariableModal(onFilterAdded, getProcessDefinition);
    const ExecutedNodeModal = createExecutedNodeModal(onFilterAdded, getDiagramXML);

    const template = <div className="create-filter">
      <Dropdown>
        <Socket name="label">
          +
        </Socket>
        <Socket name="list">
          <DropdownItem listener={DateModal.open}>
            Start Date
          </DropdownItem>
          <DropdownItem listener={VariableModal.open}>
            Variable
          </DropdownItem>
          <DropdownItem listener={ExecutedNodeModal.open}>
            Flow Nodes
          </DropdownItem>
        </Socket>
      </Dropdown>

      <DateModal />
      <VariableModal />
      <ExecutedNodeModal />
    </div>;

    return template(parentNode, eventsBus);
  };
}
