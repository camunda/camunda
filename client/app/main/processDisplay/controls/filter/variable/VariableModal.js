import {jsx, OnEvent, Socket, Scope, List, Text, Attribute, Match, Case, Default, createReferenceComponent} from 'view-utils';
import {onNextTick, isInitial} from 'utils';
import {createModal} from 'widgets';
import {loadVariables, selectVariableIdx, deselectVariableIdx, createVariableFilter} from './service';

import {OperatorSelection} from './OperatorSelection';
import {ValueInput} from './ValueInput';
import {ValueSelection} from './ValueSelection';

export function createVariableModal(createCallback, getProcessDefinition) {
  const Modal = createModal();
  const Reference = createReferenceComponent();

  let loadedDefinition;

  const VariableModal = () => {
    return (parentNode, eventBus) => {
      const template =
        <Modal>
          <Socket name="head">
            <button type="button" className="close">
              <OnEvent event="click" listener={Modal.close} />
              <span>×</span>
            </button>
            <h4 className="modal-title">New Variable Filter</h4>
          </Socket>
          <Socket name="body">
            <form>
              <OnEvent event="submit" listener={handleFormSubmit} />
              <span className="label">Variable Name</span>
              <select className="form-control">
                <Reference name="variableDropdown" />
                <OnEvent event="change" listener={changeVariable} />
                <option disabled="disabled">Please Select Variable</option>
                <Scope selector={getUnambiguosNames}>
                  <List>
                    <option>
                      <Text />
                    </option>
                  </List>
                </Scope>
              </select>

              <OperatorSelection />

              <Match>
                <Case predicate={shouldShowValueSelection}>
                  <ValueSelection />
                </Case>
                <Default>
                  <ValueInput />
                </Default>
              </Match>
            </form>
          </Socket>
          <Socket name="foot">
            <button type="button" className="btn btn-default">
              <OnEvent event="click" listener={Modal.close} />
              Abort
            </button>
            <button type="button" className="btn btn-primary">
              <OnEvent event="click" listener={createFilter} />
              <Attribute attribute="disabled" selector={isFilterInvalid} />
              Create Filter
            </button>
          </Socket>
        </Modal>;
      const templateUpdate = template(parentNode, eventBus);

      return [templateUpdate, ({variables}) => {
        const definition = getProcessDefinition();

        if (definition !== loadedDefinition || isInitial(variables)) {
          loadedDefinition = definition;
          loadVariables(definition);
        }
      }];

      function getUnambiguosNames({variables: {data}}) {
        return data && data.map(variable => variable.unambiguousName);
      }

      function handleFormSubmit({event, state}) {
        event.preventDefault();
        createFilter({state});
      }

      function shouldShowValueSelection(state) {
        return getSelectedVariableProperty(state, 'valuesAreComplete') &&
               getSelectedVariableProperty(state, 'type') !== 'Boolean';
      }

      function getSelectedVariableProperty({variables: {data}, selectedIdx}, property) {
        return selectedIdx !== undefined && data[selectedIdx][property];
      }

      function isFilterInvalid({selectedIdx, operator, values}) {
        return selectedIdx === undefined ||
          operator === undefined ||
          values === undefined ||
          values.length === 0;
      }

      function changeVariable({event:{target:{selectedIndex}}}) {
        selectVariableIdx(selectedIndex - 1); // -1 to deal with the "please select variable" entry
      }

      function createFilter({state: {variables: {data}, selectedIdx, operator, values}}) {
        const variable = data[selectedIdx];

        createVariableFilter({
          name: variable.name,
          type: variable.type,
          operator,
          values
        });

        Modal.close();

        onNextTick(createCallback);
      }
    };
  };

  VariableModal.open = () => {
    Reference.getNode('variableDropdown').selectedIndex = 0;
    deselectVariableIdx();

    Modal.open();
  };
  VariableModal.close = Modal.close;

  return VariableModal;
}
