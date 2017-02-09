import {jsx, withSelector, Scope, List, Text, Attribute, OnEvent, $window} from 'view-utils';
import {loadProcessDefinitions, selectProcessDefinition} from './service';
import {isInitial} from 'utils';

export const ProcessDefinition = withSelector(ProcessDefinitionComponent);

function ProcessDefinitionComponent({onProcessDefinitionSelected}) {
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
    // Timeout is added so that onProcessDefinitionSelected is called after
    // new state is already calculated.
    // There is also possibility to compute filter on fly, so that there is no need for timeout.
    // Although this option is better, because this code is not doing reducers job
    $window.setTimeout(onProcessDefinitionSelected);
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
