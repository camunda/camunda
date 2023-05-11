/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useState} from 'react';
import classnames from 'classnames';
import {Button} from '@carbon/react';

import {CarbonModal as Modal, Typeahead, Labeled} from 'components';
import {t} from 'translation';

import FilterSingleDefinitionSelection from '../FilterSingleDefinitionSelection';

import {BooleanInput} from './boolean';
import {NumberInput} from './number';
import {StringInput} from './string';
import {DateInput} from './date';

import './VariableFilter.scss';

export default function VariableFilter({
  addFilter,
  close,
  className,
  filterType,
  getPretext,
  getPosttext,
  config,
  filterData,
  forceEnabled,
  definitions,
}) {
  const [valid, setValid] = useState(false);
  const [filter, setFilter] = useState({});
  const [variables, setVariables] = useState([]);
  const [selectedVariable, setSelectedVariable] = useState(null);
  const [applyTo, setApplyTo] = useState(null);

  // load the available variables for the selected definition
  useEffect(() => {
    (async () => {
      const validDefinitions = definitions?.filter(
        (definition) => definition.versions.length && definition.tenantIds.length
      );

      const applyTo =
        validDefinitions?.find(({identifier}) => filterData?.appliedTo[0] === identifier) ||
        validDefinitions?.[0];

      setVariables(await config.getVariables(applyTo));
      setApplyTo(applyTo);
    })();
  }, [config, definitions, filterData?.appliedTo]);

  // check if the all the variable filters are valid on filters change
  useEffect(() => {
    if (selectedVariable && filter) {
      const InputComponent = getInputComponentForVariable(selectedVariable);
      const isFilterValid = InputComponent.isValid(filter);
      setValid(isFilterValid);
    }
  }, [filter, selectedVariable]);

  // initialize the filters state when editing a pre-existing filter
  useEffect(() => {
    if (filterData) {
      const data = filterData.data;

      const InputComponent = getInputComponentForVariable(data);
      const filter = InputComponent.parseFilter
        ? InputComponent.parseFilter(filterData)
        : data.data;

      const {id, name, type} = data;
      setSelectedVariable({id, name, type});
      setFilter(filter);
      setValid(true);
    }
  }, [filterData]);

  const selectVariable = (nameOrId) => {
    const variable = variables.find((variable) => getId(variable) === nameOrId);

    setSelectedVariable(variable);
    setFilter(getInputComponentForVariable(variable).defaultFilter);
  };

  const getInputComponentForVariable = (variable) => {
    if (!variable) {
      return () => null;
    }

    switch (variable.type.toLowerCase()) {
      case 'string':
        return StringInput;
      case 'boolean':
        return BooleanInput;
      case 'date':
        return DateInput;
      default:
        return NumberInput;
    }
  };

  const changeFilter = (filter) => setFilter(filter);

  const getId = (variable) => {
    if (variable) {
      return variable.id || variable.name;
    }
  };

  const getVariableName = (variable) => (variable ? variable.label || variable.name : null);

  const createFilter = (evt) => {
    evt.preventDefault();

    const InputComponent = getInputComponentForVariable(selectedVariable);
    InputComponent.addFilter
      ? InputComponent.addFilter(addFilter, filterType, selectedVariable, filter, applyTo)
      : addFilter({
          type: filterType,
          data: {
            name: selectedVariable.id || selectedVariable.name,
            type: selectedVariable.type,
            data: filter,
          },
          appliedTo: [applyTo?.identifier],
        });
  };

  const ValueInput = getInputComponentForVariable(selectedVariable);

  return (
    <Modal
      isOverflowVisible
      open
      onClose={close}
      className={classnames('VariableFilter__modal', className)}
    >
      <Modal.Header>
        {t('common.filter.modalHeader', {
          type: t(`common.filter.types.${filterType}`),
        })}
      </Modal.Header>
      <Modal.Content>
        {definitions && (
          <FilterSingleDefinitionSelection
            availableDefinitions={definitions}
            applyTo={applyTo}
            setApplyTo={async (applyTo) => {
              setApplyTo(applyTo);
              setValid(false);
              setFilter({});
              setVariables([]);
              setVariables(await config.getVariables(applyTo));
              setSelectedVariable(null);
            }}
          />
        )}
        {getPretext?.(selectedVariable)}
        <Labeled className="LabeledTypeahead" label={t('common.filter.variableModal.inputLabel')}>
          <Typeahead
            onChange={selectVariable}
            value={variables.length > 0 && getId(selectedVariable)}
            placeholder={t('common.filter.variableModal.inputPlaceholder')}
            noValuesMessage={t('common.filter.variableModal.noVariables')}
          >
            {variables.map((variable) => (
              <Typeahead.Option key={getId(variable)} value={getId(variable)}>
                {getVariableName(variable)}
              </Typeahead.Option>
            ))}
          </Typeahead>
        </Labeled>
        <ValueInput
          config={config}
          variable={selectedVariable}
          changeFilter={changeFilter}
          filter={filter}
          definition={applyTo}
        />
        {getPosttext?.(selectedVariable)}
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" className="cancel" onClick={close}>
          {t('common.cancel')}
        </Button>
        <Button
          className="confirm"
          disabled={!valid && !forceEnabled?.(selectedVariable)}
          onClick={createFilter}
        >
          {filterData ? t('common.filter.updateFilter') : t('common.filter.addFilter')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
