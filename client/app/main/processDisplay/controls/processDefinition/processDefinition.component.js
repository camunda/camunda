import {jsx, withSelector, Select, List, Text, Attribute, OnEvent} from 'view-utils';
import {loadProcessDefinitions, selectProcessDefinition} from './processDefinition.service';
import {INITIAL_STATE} from 'utils/loading';

export const ProcessDefinition = withSelector(ProcessDefinitionComponent);

export function ProcessDefinitionComponent() {
  const template = <td>
    <select className="form-control">
      <OnEvent event="change" listener={select} />
      <option disabled="" selected="">Select Process</option>
      <Select selector={getAvailableDefinitions}>
        <List>
          <option>
            <Attribute selector="id" attribute="value" />
            <Text property="name" />
          </option>
        </List>
      </Select>
    </select>
  </td>;

  function select({node}) {
    selectProcessDefinition(node.value);
  }

  function getAvailableDefinitions({availableProcessDefinitions}) {
    return availableProcessDefinitions.data;
  }

  function update(parentNode, eventsBus) {
    const templateUpdate = template(parentNode, eventsBus);

    return [templateUpdate, ({availableProcessDefinitions}) => {
      if (availableProcessDefinitions.state === INITIAL_STATE) {
        loadProcessDefinitions();
      }
    }];
  }

  return update;
}
