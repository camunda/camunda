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

    return [templateUpdate, ({values}) => {
      const inputs = parentNode.querySelectorAll('.value-list-label input');

      for (let i = 0; i < inputs.length; i++) {
        const input = inputs[i];

        input.checked = includes(values, input.parentNode.textContent);
      }
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
