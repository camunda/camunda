import {jsx, Socket} from 'view-utils';
import {Dropdown, DropdownItem} from 'widgets';
import {createDateModal} from './date';
import {createVariableModal} from './variable';

export function CreateFilter({onFilterAdded, getProcessDefinition}) {
  return (parentNode, eventsBus) => {
    const DateModal = createDateModal(onFilterAdded);
    const VariableModal = createVariableModal(onFilterAdded, getProcessDefinition);

    const template = <td>
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
        </Socket>
      </Dropdown>

      <DateModal />
      <VariableModal />
    </td>;

    return template(parentNode, eventsBus);
  };
}
