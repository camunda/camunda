/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState, useEffect} from 'react';
import classnames from 'classnames';
import {Button} from '@carbon/react';

import {Modal} from 'components';
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
  const [expandedFilter, setExpandedFilter] = useState(0);
  const [variables, setVariables] = useState([]);
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
    const isValid = filters.every((filter) => {
      if (!filter.data) {
        return false;
      }
      const InputComponent = getInputComponentForVariable(filter.type);
      return InputComponent.isValid(filter.data);
    });

    setValid(isValid);
  }, [filters]);

  // initialize the filters state when editing a pre-existing filter
  useEffect(() => {
    if (filterData?.data) {
      const filtersToAdd = filterData.data.data.map((filter) => {
        const InputComponent = getInputComponentForVariable(filter.type);
        return InputComponent.parseFilter
          ? {name: filter.name, type: filter.type, data: InputComponent.parseFilter({data: filter})}
          : filter;
      });

      setExpandedFilter(filtersToAdd.length === 1 ? 0 : -1);
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

    const filterToAdd = [];
    filters.forEach((filter) => {
      const InputComponent = getInputComponentForVariable(filter.type);
      if (InputComponent.addFilter) {
        InputComponent.addFilter(
          (adjustedFilter) => filterToAdd.push(adjustedFilter.data),
          filterType,
          {name: filter.name, type: filter.type},
          filter.data,
          applyTo
        );
      } else {
        filterToAdd.push(filter);
      }
    });

    setFilters(filterToAdd);
    addFilter({
      type: 'multipleVariable',
      data: {data: filterToAdd},
      appliedTo: [applyTo?.identifier],
    });
  };

  const updateFilterData = (updateIndex, newFilter) => {
    if (updateIndex !== expandedFilter) {
      setExpandedFilter(updateIndex);
    }
    setFilters(filters.map((filter, idx) => (idx === updateIndex ? newFilter : filter)));
  };

  return (
    <Modal
      isOverflowVisible
      open
      onClose={close}
      className={classnames('MultipleVariableFilterModal', className)}
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
              setFilters([{}]);
              setVariables([]);
              setVariables(await config.getVariables(applyTo));
            }}
          />
        )}
        <div className="info">{t('common.filter.variableModal.info')}</div>
        {filters.map((filter, idx) => (
          <div className="variableContainer" key={filter.name + filter.type}>
            {idx !== 0 && <span className="orOperator">{t('common.filter.variableModal.or')}</span>}
            <FilterInstance
              expanded={idx === expandedFilter}
              toggleExpanded={() => {
                setExpandedFilter(idx === expandedFilter ? -1 : idx);
              }}
              filter={filter}
              updateFilterData={(newFilter) => updateFilterData(idx, newFilter)}
              variables={variables}
              config={config}
              applyTo={applyTo}
              filters={filters}
              onRemove={() => {
                setExpandedFilter(-1);
                setFilters(filters.filter((_, filterIdx) => filterIdx !== idx));
              }}
            />
          </div>
        ))}
        <Button
          className="orButton"
          disabled={!valid || variables.length <= filters.length}
          onClick={() => {
            setExpandedFilter(filters.length);
            setFilters([...filters, {}]);
          }}
        >
          + {t('common.filter.variableModal.or')}
        </Button>
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" className="cancel" onClick={close}>
          {t('common.cancel')}
        </Button>
        <Button className="confirm" disabled={!valid} onClick={createFilter}>
          {filterData ? t('common.filter.updateFilter') : t('common.filter.addFilter')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
