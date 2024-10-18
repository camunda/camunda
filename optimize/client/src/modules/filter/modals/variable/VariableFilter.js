/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import classnames from 'classnames';
import {Button, ComboBox, InlineNotification, Stack, TextInputSkeleton} from '@carbon/react';

import {Modal} from 'components';
import {t} from 'translation';

import FilterSingleDefinitionSelection from '../FilterSingleDefinitionSelection';

import {BooleanInput} from './boolean';
import {NumberInput} from './number';
import {StringInput} from './string';
import {DateInput} from './date';

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
  const [variables, setVariables] = useState(null);
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

  const selectVariable = ({selectedItem}) => {
    setSelectedVariable(selectedItem);
    setFilter(getInputComponentForVariable(selectedItem).defaultFilter);
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

  const getVariableName = (variable) => (variable ? variable.label || variable.name : null);

  const createFilter = (evt) => {
    evt.preventDefault();

    const InputComponent = getInputComponentForVariable(selectedVariable);
    if (InputComponent.addFilter) {
      InputComponent.addFilter(addFilter, filterType, selectedVariable, filter, applyTo);
    } else {
      addFilter({
        type: filterType,
        data: {
          name: selectedVariable.id || selectedVariable.name,
          type: selectedVariable.type,
          data: filter,
        },
        appliedTo: [applyTo?.identifier],
      });
    }
  };

  const ValueInput = getInputComponentForVariable(selectedVariable);

  return (
    <Modal
      isOverflowVisible
      open
      onClose={close}
      className={classnames('VariableFilter__modal', className)}
    >
      <Modal.Header
        title={t('common.filter.modalHeader', {
          type: t(`common.filter.types.${filterType}`),
        })}
      />
      <Modal.Content>
        <Stack gap={6}>
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
          {variables ? (
            <>
              {!variables.length && (
                <InlineNotification
                  kind="warning"
                  hideCloseButton
                  subtitle={t('common.filter.variableModal.noVariables')}
                />
              )}
              <ComboBox
                id="variableSelection"
                titleText={t('common.filter.variableModal.inputLabel')}
                placeholder={t('common.filter.variableModal.inputPlaceholder')}
                disabled={!variables.length}
                items={variables}
                itemToString={getVariableName}
                selectedItem={selectedVariable}
                onChange={selectVariable}
              />
              <ValueInput
                config={config}
                variable={selectedVariable}
                changeFilter={changeFilter}
                filter={filter}
                definition={applyTo}
              />
            </>
          ) : (
            <TextInputSkeleton />
          )}
          {getPosttext?.(selectedVariable)}
        </Stack>
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
