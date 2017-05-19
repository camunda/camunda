import {jsx, Scope, List, Text, OnEvent} from 'view-utils';
import {setValue} from './service';

export function ValueSelection() {
  const template = <div className="variable-value">
    <Scope selector={getVariableList}>
      <List>
        <label className="value-list-label">
          <input type="radio" name="value">
            <OnEvent event="click" listener={applyValue} />
          </input>
          <Scope selector={value => {return {value};}}>
            <Text property="value" />
          </Scope>
        </label>
      </List>
    </Scope>
  </div>;

  return (parentNode, eventBus) => {
    const templateUpdate = template(parentNode, eventBus);

    return [templateUpdate, ({value}) => {
      const inputs = parentNode.querySelectorAll('.value-list-label input');

      inputs.forEach(input => {
        input.checked = input.labels[0].textContent === value;
      });
    }];
  };

  function getVariableList({variables: {data}, selectedIdx}) {
    return data[selectedIdx].values;
  }

  function applyValue({state}) {
    setValue(state);
  }
}
