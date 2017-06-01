import {jsx, Scope, List, Text, OnEvent, includes, Attribute} from 'view-utils';
import {addValue, removeValue, setValue, operatorCanHaveMultipleValues} from './service';

export function ValueSelection() {
  const template = <div className="variable-value">
    <Scope selector={getVariableList}>
      <List>
        <label className="value-list-label">
          <input name="value">
            <Attribute attribute="type" selector={getTypeByOperator} />
            <OnEvent event="click" listener={applyValue} />
          </input>
          <Text property="value" />
        </label>
      </List>
    </Scope>
  </div>;

  return (parentNode, eventBus) => {
    const templateUpdate = template(parentNode, eventBus);

    return [templateUpdate, ({value}) => {
      const inputs = parentNode.querySelectorAll('.value-list-label input');

      inputs.forEach(input => {
        input.checked = includes(value, input.labels[0].textContent);
      });
    }];
  };

  function getTypeByOperator({operator}) {
    if (operatorCanHaveMultipleValues(operator)) {
      return 'checkbox';
    }
    return 'radio';
  }

  function getVariableList({variables: {data}, selectedIdx, operator}) {
    return data[selectedIdx].values.map(value => {
      return {
        value,
        operator
      };
    });
  }

  function applyValue({state: {value, operator}, node: {checked}}) {
    if (operatorCanHaveMultipleValues(operator)) {
      if (checked) {
        addValue(value);
      } else {
        removeValue(value);
      }
    } else {
      setValue(value);
    }
  }
}
