/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {FieldsModal} from './FieldsModal';
import type {NamedCustomFilters} from 'modules/custom-filters/customFiltersSchema';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';
import {FilterNameModal} from './FilterNameModal';
import {DeleteFilterModal} from './DeleteFilterModal';
import {tracking} from 'modules/tracking';

const DEFAULT_FILTER_VALUES: NamedCustomFilters = {
  assignee: 'all',
  status: 'all',
};

function getCustomFilterParams(
  filterId: string | undefined,
): NamedCustomFilters {
  const storedFilters = getStateLocally('customFilters');

  if (filterId === undefined || storedFilters === null) {
    return DEFAULT_FILTER_VALUES;
  }

  return storedFilters[filterId] ?? DEFAULT_FILTER_VALUES;
}

function useCustomFilters(options: {isOpen: boolean; filterId?: string}) {
  const {isOpen, filterId} = options;
  const [customFilters, setCustomFilters] = useState<NamedCustomFilters>(
    getCustomFilterParams(filterId),
  );

  useEffect(() => {
    if (isOpen) {
      setCustomFilters(getCustomFilterParams(filterId));
    }
  }, [isOpen, filterId]);

  return [customFilters, setCustomFilters] as const;
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
  const [customFilters, setCustomFilters] = useCustomFilters({
    isOpen,
    filterId,
  });
  const [currentStep, setCurrentStep] = useState<'fields' | 'name' | 'delete'>(
    'fields',
  );

  return (
    <>
      <FieldsModal
        isOpen={isOpen && currentStep === 'fields'}
        initialValues={customFilters}
        onApply={(filters) => {
          storeStateLocally('customFilters', {
            ...getStateLocally('customFilters'),
            custom: filters,
          });
          tracking.track({
            eventName: 'custom-filter-applied',
          });
          onSuccess('custom');
        }}
        onSave={(filters) => {
          setCustomFilters(filters);
          setCurrentStep('name');
        }}
        onEdit={(filters) => {
          setCurrentStep('fields');

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

          tracking.track({
            eventName: 'custom-filter-updated',
          });

          onSuccess(filterId!);
        }}
        onDelete={() => {
          setCurrentStep('delete');
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
          setCurrentStep('fields');

          tracking.track({
            eventName: 'custom-filter-saved',
          });

          onSuccess(filterId);
        }}
        onCancel={() => setCurrentStep('fields')}
      />
      <DeleteFilterModal
        isOpen={isOpen && currentStep === 'delete'}
        onClose={() => setCurrentStep('fields')}
        onDelete={() => {
          storeStateLocally(
            'customFilters',
            Object.fromEntries(
              Object.entries(getStateLocally('customFilters') ?? {}).filter(
                ([name]) => name !== filterId,
              ),
            ),
          );
          tracking.track({
            eventName: 'custom-filter-deleted',
          });
          setCurrentStep('fields');
          onDelete();
        }}
        filterName={filterId ?? ''}
      />
    </>
  );
};

export {CustomFiltersModal};
