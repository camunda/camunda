/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import {Button, Form, InlineNotification, Stack} from '@carbon/react';

import {t} from 'translation';
import {Modal, DateRangeInput} from 'components';
import {Definition, Filter, FilterState} from 'types';

import {FilterProps} from '../types';
import FilterDefinitionSelection from '../FilterDefinitionSelection';

import DateFilterPreview from './DateFilterPreview';
import {convertFilterToState, convertStateToFilter, isValid} from './service';

import './DateFilter.scss';

interface DateFilterProps extends FilterProps<Partial<Filter>> {
  filterType: 'instanceStartDate' | 'instanceEndDate';
  filterLevel: 'instance';
}

type DateFilterState = FilterState & {
  applyTo?: Definition[] | string[];
};

export default function DateFilter({
  filterData,
  filterType,
  definitions,
  addFilter,
  close,
}: DateFilterProps) {
  const [filterState, setFilterState] = useState<DateFilterState>(() => {
    let initialData = {};
    const defaultState = {
      valid: false,
      type: '',
      unit: '',
      customNum: '2',
      startDate: null,
      endDate: null,
      applyTo: [
        {identifier: 'all', displayName: t('common.filter.definitionSelection.allProcesses')},
      ],
    };

    if (filterData) {
      initialData = convertFilterToState(filterData.data);

      if (filterData.appliedTo?.[0] !== 'all') {
        initialData = {
          ...initialData,
          applyTo: filterData.appliedTo
            .map((id) => definitions.find(({identifier}) => identifier === id))
            .filter((definition): definition is Definition => !!definition),
        };
      }
    }

    return {...defaultState, ...initialData};
  });

  const confirm = () => {
    const {type, unit, customNum, startDate, endDate, applyTo} = filterState;
    if (isValid(filterState)) {
      return addFilter({
        type: filterType,
        data: convertStateToFilter({type, unit, customNum, startDate, endDate} as FilterState),
        appliedTo: applyTo?.map(({identifier}) => identifier) || [],
      });
    }
  };

  const {type, unit, customNum, startDate, endDate, applyTo} = filterState;

  return (
    <Modal size="sm" open onClose={close} isOverflowVisible className="DateFilter">
      <Modal.Header>
        {t('common.filter.modalHeader', {
          type: t(`common.filter.types.${filterType}`),
        })}
      </Modal.Header>
      <Modal.Content>
        <Stack gap={6}>
          <FilterDefinitionSelection
            availableDefinitions={definitions}
            applyTo={applyTo as Definition[]}
            setApplyTo={(applyTo) => setFilterState({...filterState, applyTo})}
          />
          {filterType === 'instanceEndDate' && (
            <InlineNotification
              kind="warning"
              subtitle={t('common.filter.dateModal.endDateWarning').toString()}
            />
          )}
          <Form>
            <Stack gap={6}>
              <span className="tip">{t(`common.filter.dateModal.info.${filterType}`)}</span>
              <DateRangeInput
                type={type}
                unit={unit}
                startDate={startDate}
                endDate={endDate}
                customNum={customNum}
                onChange={(change) => setFilterState({...filterState, ...change})}
              />
              {isValid(filterState) && (
                <DateFilterPreview
                  filterType={filterType}
                  filter={convertStateToFilter({
                    type,
                    unit,
                    customNum,
                    startDate,
                    endDate,
                  })}
                />
              )}
            </Stack>
          </Form>
        </Stack>
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" className="cancel" onClick={close}>
          {t('common.cancel')}
        </Button>
        <Button className="confirm" disabled={!isValid(filterState)} onClick={confirm}>
          {filterData ? t('common.filter.updateFilter') : t('common.filter.addFilter')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
