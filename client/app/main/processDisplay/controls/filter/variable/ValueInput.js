import {jsx, includes, Attribute, OnEvent} from 'view-utils';
import {setValue} from './service';

export function ValueInput() {
  return <div className="variable-value">
    <input className="form-control" placeholder="Enter value">
      <Attribute attribute="type" selector={getInputType} />
      <Attribute attribute="value" selector={getValue} />
      <OnEvent event="input" listener={changeValue} />
    </input>
  </div>;

  function changeValue({event: {target: {value}}, state}) {
    const inputType = getInputType(state);
    let parsedValue = value;

    if (inputType === 'number') {
      parsedValue = parseFloat(parsedValue);
    }

    setValue(parsedValue);
  }

  function getValue({value}) {
    return value || '';
  }

  function getInputType({variables: {data}, selectedIdx}) {
    const variableType = data && selectedIdx !== undefined && data[selectedIdx].type;

    if (!variableType || variableType === 'Boolean') {
      return 'hidden';
    }
    if (includes(['Short', 'Integer', 'Long', 'Double'], variableType)) {
      return 'number';
    }

    return 'text';
  }
}
