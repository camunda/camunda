import {jsx, includes, Attribute, OnEvent, Class, Scope, List} from 'view-utils';
import {onNextTick} from 'utils';
import {setValue, addValue} from './service';

export function ValueInput() {
  return (node, eventBus) => {
    const template = <div className="variable-value">
      <Scope selector={getValueList}>
        <List>
          <input className="form-control" placeholder="Enter value">
            <Attribute attribute="type" selector="type" />
            <Attribute attribute="value" selector="value" />
            <OnEvent event="input" listener={changeValue} />
          </input>
        </List>
      </Scope>
      <button className="btn btn-link add-another-btn">
        <Class className="hidden" predicate={shouldNotDisplayAddValueButton} />
        <OnEvent event="click" listener={addValueField} />
        + Add another value
      </button>
    </div>;

    const templateUpdate = template(node, eventBus);

    return templateUpdate;

    function getValueList({variables: {data}, selectedIdx, values}) {
      const variableType = data && selectedIdx !== undefined && data[selectedIdx].type;
      let type = 'text';

      if (!variableType || variableType === 'Boolean') {
        type = 'hidden';
      }
      if (includes(['Short', 'Integer', 'Long', 'Double'], variableType)) {
        type = 'number';
      }

      return values.map((value, idx) => {
        return {
          value,
          idx,
          type
        };
      });
    }

    function addValueField({event}) {
      event.preventDefault();
      addValue('');
    }

    function shouldNotDisplayAddValueButton({variables: {data}, selectedIdx, values}) {
      return !values[values.length - 1] ||
             !data ||
             selectedIdx === undefined ||
             data[selectedIdx].type !== 'String';
    }

    function changeValue({event: {target: {value}}, state}) {
      const inputType = state.type;
      let parsedValue = value;

      if (inputType === 'number') {
        parsedValue = parseFloat(parsedValue);
      }

      setValue(parsedValue, state.idx);

      // setValue will cause a state update that updates the template so that
      // the original input will be unfocussed. We need to re-focus it manually.
      onNextTick(() => {
        document.querySelectorAll('.variable-value input')[state.idx].focus();
      });
    }
  };
}
