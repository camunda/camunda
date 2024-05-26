/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {FieldsModal} from './FieldsModal';
import type {CustomFilters} from 'modules/custom-filters/customFiltersSchema';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';
import {FilterNameModal} from './FilterNameModal';
import {DeleteFilterModal} from './DeleteFilterModal';

const DEFAULT_FILTER_VALUES: CustomFilters = {
  assignee: 'all',
  status: 'all',
};

function getCustomFilterParams(filterId: string | undefined): CustomFilters {
  const storedFilters = getStateLocally('customFilters');

  if (filterId === undefined || storedFilters === null) {
    return DEFAULT_FILTER_VALUES;
  }

  return storedFilters[filterId] ?? DEFAULT_FILTER_VALUES;
}

type Props = Omit<
  React.ComponentProps<typeof FieldsModal>,
  'initialValues' | 'onSave' | 'onApply' | 'onEdit'
> & {
  filterId?: string;
  onSuccess: (filterId: string) => void;
};

const CustomFiltersModal: React.FC<Props> = ({
  filterId,
  isOpen,
  onSuccess,
  onDelete,
  ...props
}) => {
  const [customFilters, setCustomFilters] = useState<CustomFilters>(
    DEFAULT_FILTER_VALUES,
  );
  const [currentStep, setCurrentStop] = useState<'fields' | 'name' | 'delete'>(
    'fields',
  );

  return (
    <>
      <FieldsModal
        isOpen={isOpen && currentStep === 'fields'}
        initialValues={getCustomFilterParams(filterId)}
        onApply={(filters) => {
          storeStateLocally('customFilters', {
            ...getStateLocally('customFilters'),
            custom: filters,
          });
          onSuccess('custom');
        }}
        onSave={(filters) => {
          setCustomFilters(filters);
          setCurrentStop('name');
        }}
        onEdit={(filters) => {
          setCurrentStop('fields');
          setCustomFilters(DEFAULT_FILTER_VALUES);

          if (filterId === undefined) {
            console.error('Filter ID is undefined on edit');
            props.onClose();

            return;
          }

          storeStateLocally('customFilters', {
            ...getStateLocally('customFilters'),
            [filterId!]: {
              ...filters,
            },
          });

          onSuccess(filterId!);
        }}
        onDelete={() => {
          setCustomFilters(DEFAULT_FILTER_VALUES);
          setCurrentStop('delete');
        }}
        {...props}
      />
      <FilterNameModal
        isOpen={isOpen && currentStep === 'name'}
        onApply={(filterName) => {
          const filterId = crypto.randomUUID();
          storeStateLocally('customFilters', {
            ...getStateLocally('customFilters'),
            [filterId]: {
              ...customFilters,
              name: filterName,
            },
          });
          setCurrentStop('fields');
          setCustomFilters(DEFAULT_FILTER_VALUES);
          onSuccess(filterId);
        }}
        onCancel={() => setCurrentStop('fields')}
      />
      <DeleteFilterModal
        isOpen={isOpen && currentStep === 'delete'}
        onClose={() => setCurrentStop('fields')}
        onDelete={() => {
          storeStateLocally(
            'customFilters',
            Object.fromEntries(
              Object.entries(getStateLocally('customFilters') ?? {}).filter(
                ([name]) => name !== filterId,
              ),
            ),
          );
          setCurrentStop('fields');
          onDelete();
        }}
        filterName={filterId ?? ''}
      />
    </>
  );
};

export {CustomFiltersModal};
