import {jsx, withSelector, Scope, List, Text, Attribute, OnEvent} from 'view-utils';
import {loadProcessDefinitions, selectProcessDefinition} from './service';
import {isInitial} from 'utils/loading';

export const ProcessDefinition = withSelector(ProcessDefinitionComponent);

export function ProcessDefinitionComponent() {
  const template = <td>
    <select className="form-control">
      <OnEvent event="change" listener={select} />
      <option disabled="" value="">Select Process</option>
      <Scope selector={getAvailableDefinitions}>
        <List>
          <option>
            <Attribute selector="id" attribute="value" />
            <Text property="name" />
          </option>
        </List>
      </Scope>
    </select>
  </td>;

  function select({node}) {
    selectProcessDefinition(node.value);
  }

  function getAvailableDefinitions({availableProcessDefinitions}) {
    return availableProcessDefinitions.data;
  }

  return (parentNode, eventsBus) => {
    const templateUpdate = template(parentNode, eventsBus);

    const selectField = parentNode.querySelector('select');

    return [templateUpdate, ({availableProcessDefinitions, selected}) => {
      selectField.value = selected || '';

      if (isInitial(availableProcessDefinitions)) {
        loadProcessDefinitions();
      }
    }];
  };
}
