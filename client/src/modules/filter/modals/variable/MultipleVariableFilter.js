/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect} from 'react';
import classnames from 'classnames';
import {Modal, Button} from 'components';
import {t} from 'translation';

import FilterSingleDefinitionSelection from '../FilterSingleDefinitionSelection';
import FilterInstance from './FilterInstance';
import {BooleanInput} from './boolean';
import {NumberInput} from './number';
import {StringInput} from './string';
import {DateInput} from './date';

import './MultipleVariableFilter.scss';

export default function MultipleVariableFilter({
  close,
  className,
  filterType,
  config,
  filterData,
  definitions,
  addFilter,
}) {
  const [valid, setValid] = useState(false);
  const [filters, setFilters] = useState([{}]);
  const [variables, setVariables] = useState([]);
  const [applyTo, setApplyTo] = useState(null);

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

  useEffect(() => {
    const isValid = filters.every((filter) => {
      if (!filter.data) {
        return false;
      }
      const InputComponent = getInputComponentForVariable(filter.type);
      return InputComponent.isValid(filter.data);
    });

    setValid(isValid);
  }, [filters]);

  useEffect(() => {
    if (filterData?.data) {
      const filtersToAdd = filterData.data.data.map((filter) => {
        const InputComponent = getInputComponentForVariable(filter.type);
        return InputComponent.parseFilter
          ? {name: filter.name, type: filter.type, data: InputComponent.parseFilter({data: filter})}
          : filter;
      });

      setFilters(filtersToAdd);
      setValid(true);
    }
  }, [filterData]);

  function getInputComponentForVariable(type) {
    if (!type) {
      return () => null;
    }

    switch (type.toLowerCase()) {
      case 'string':
        return StringInput;
      case 'boolean':
        return BooleanInput;
      case 'date':
        return DateInput;
      default:
        return NumberInput;
    }
  }

  const createFilter = (evt) => {
    evt.preventDefault();

    let filterToAdd = filters;
    const updateFilter = (newFilter) => {
      filterToAdd = filterToAdd.map((filter) => {
        if (filter.type === newFilter.data.type && filter.name === newFilter.data.name) {
          return newFilter.data;
        }
        return filter;
      });
    };

    filters.forEach((filter) => {
      const InputComponent = getInputComponentForVariable(filter.type);
      if (InputComponent.addFilter) {
        InputComponent.addFilter(
          updateFilter,
          filterType,
          {name: filter.name, type: filter.type},
          filter.data,
          applyTo
        );
      }
    });

    setFilters(filterToAdd);
    addFilter({
      type: 'multipleVariable',
      data: {data: filterToAdd},
      appliedTo: [applyTo?.identifier],
    });
  };

  const updateFilterData = (nameOrId, type) => (newFilter) => {
    setFilters(
      filters.map((filter) =>
        filter.name === nameOrId && filter.type === type ? newFilter : filter
      )
    );
  };

  return (
    <Modal open onClose={close} className={classnames('MultipleVariableFilterModal', className)}>
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
              setFilters([]);
              setVariables([]);
              setVariables(await config.getVariables(applyTo));
            }}
          />
        )}
        {filters.map((filter, idx) => (
          <div key={idx}>
            {idx !== 0 && <span className="orOperator">{t('common.filter.variableModal.or')}</span>}
            <FilterInstance
              filter={filter}
              updateFilterData={updateFilterData(filter.name, filter.type)}
              variables={variables}
              config={config}
              applyTo={applyTo}
              filters={filters}
            />
          </div>
        ))}
        <Button
          className="orButton"
          small
          disabled={!valid}
          onClick={() => {
            setFilters([...filters, {}]);
          }}
        >
          + {t('common.filter.variableModal.or')}
        </Button>
      </Modal.Content>
      <Modal.Actions>
        <Button main onClick={close}>
          {t('common.cancel')}
        </Button>
        <Button main primary disabled={!valid} onClick={createFilter}>
          {filterData ? t('common.filter.updateFilter') : t('common.filter.addFilter')}
        </Button>
      </Modal.Actions>
    </Modal>
  );
}
