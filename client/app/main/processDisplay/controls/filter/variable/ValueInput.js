import {jsx, OnEvent, Class, Scope, List, createReferenceComponent} from 'view-utils';
import {onNextTick} from 'utils';
import {setValue, addValue} from './service';

export function ValueInput() {
  return (node, eventBus) => {
    const Reference = createReferenceComponent();

    const template = <div className="variable-value">
      <Reference name="variableValue" />
      <Scope selector={getValueList}>
        <List>
          <input className="form-control" placeholder="Enter value" type="text">
            <Class className="hidden" predicate={shouldNotDisplayInput} />
            <OnEvent event="input" listener={changeValue} />
          </input>
        </List>
      </Scope>
      <button type="button" className="btn btn-link add-another-btn">
        <Class className="hidden" predicate={shouldNotDisplayAddValueButton} />
        <OnEvent event="click" listener={addValueField} />
        + Add another value
      </button>
    </div>;

    const templateUpdate = template(node, eventBus);

    return [templateUpdate, ({values}) => {
      const nodes = Reference.getNode('variableValue').querySelectorAll('input');

      for (let i = 0; i < nodes.length; i++) {
        nodes[i].value = typeof values[i] === 'undefined' ? '' : values[i];
      }
    }];

    function getValueList({variables: {data}, selectedIdx, values}) {
      const variableType = data && selectedIdx !== undefined && data[selectedIdx].type;
      let type = 'text';

      if (!variableType || variableType === 'Boolean') {
        type = 'hidden';
      }

      // should always have at least one entry
      const processedValues = values.length === 0 ? [''] : values;

      return processedValues.map((value, idx) => {
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

    function shouldNotDisplayInput({type}) {
      return type === 'hidden';
    }

    function shouldNotDisplayAddValueButton({variables: {data}, selectedIdx, values}) {
      return !values[values.length - 1] ||
             !data ||
             selectedIdx === undefined ||
             data[selectedIdx].type !== 'String';
    }

    function changeValue({event: {target}, state}) {
      const selectionStart = target.selectionStart;
      const selectionEnd = target.selectionEnd;

      setValue(target.value, state.idx);

      // setValue will cause a state update that updates the template so that
      // the original input will be unfocussed. We need to re-focus it manually.
      onNextTick(() => {
        const element = document.querySelectorAll('.variable-value input')[state.idx];

        element.focus();
        element.setSelectionRange(selectionStart, selectionEnd);
      });
    }
  };
}
