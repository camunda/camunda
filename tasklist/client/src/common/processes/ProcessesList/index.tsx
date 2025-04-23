/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  Search,
  Grid,
  Column,
  Stack,
  Link,
  Layer,
  SkeletonPlaceholder,
  Dropdown,
} from '@carbon/react';
import {ProcessTile} from 'common/processes/ProcessTile';
import {MultitenancyDropdown} from 'common/multitenancy/MultitenancyDropdown';
import styles from './styles.module.scss';
import {useTranslation} from 'react-i18next';
import {useIsMultitenancyEnabled} from 'common/multitenancy/useIsMultitenancyEnabled';
import {C3EmptyState} from '@camunda/camunda-composite-components';
import EmptyMessageImage from './empty-message-image.svg';
import {tracking} from 'common/tracking';
import {START_FORM_FILTER_OPTIONS, type FilterOption} from './constants';
import {getMultiModeProcessDisplayName} from 'common/processes/getMultiModeProcessDisplayName';
import type {MultiModeProcess} from 'common/processes';

const FilterDropdown: React.FC<{
  items: FilterOption[];
  selected?: FilterOption;
  onChange?: (option: FilterOption) => void;
}> = ({items, selected, onChange}) => {
  const {t} = useTranslation();

  return (
    <Dropdown
      id="process-filters"
      data-testid="process-filters"
      className={styles.dropdown}
      hideLabel
      selectedItem={selected}
      titleText={t('processesFilterDropdownLabel')}
      label={t('processesFilterDropdownLabel')}
      items={items}
      itemToString={(item) => (item ? t(item.textKey) : '')}
      onChange={(data) => {
        if (data.selectedItem && onChange) {
          onChange(data.selectedItem);
        }
      }}
    />
  );
};

interface Props<Process extends MultiModeProcess> {
  processes: Process[];
  searchValue: string;
  isLoading: boolean;
  isFiltered: boolean;
  onSearch: (event: {target: HTMLInputElement; type: 'change'}) => void;
  initialTenant: React.ComponentProps<
    typeof MultitenancyDropdown
  >['initialSelectedItem'];
  onTenantChange: (tenant: string) => void;
  startFormFilterValue: FilterOption;
  onStartFormFilterChange: (filter: FilterOption) => void;
  isStartButtonDisabled: boolean;
  getUniqueId: (process: Process) => string;
  onStartProcess: (process: Process) => () => void;
  onStartProcessError: (process: Process) => () => void;
  onStartProcessSuccess: () => void;
  selectedTenantId: string | undefined;
  getStartProcessStatus: (
    process: Process,
  ) => 'inactive' | 'active' | 'finished' | 'error' | 'active-tasks';
}

function ProcessesList<Process extends MultiModeProcess>({
  processes,
  searchValue,
  isLoading,
  isFiltered,
  onSearch,
  initialTenant,
  onTenantChange,
  startFormFilterValue,
  onStartFormFilterChange,
  isStartButtonDisabled,
  getUniqueId,
  onStartProcess,
  onStartProcessError,
  onStartProcessSuccess,
  selectedTenantId,
  getStartProcessStatus,
}: Props<Process>) {
  const {t} = useTranslation();
  const {isMultitenancyEnabled} = useIsMultitenancyEnabled();
  const processSearchProps: React.ComponentProps<typeof Search> = {
    size: 'md',
    placeholder: t('processesFilterFieldLabel'),
    labelText: t('processesFilterFieldLabel'),
    closeButtonLabelText: t('processesClearFilterFieldButtonLabel'),
    value: searchValue,
    onChange: onSearch,
    disabled: isLoading,
  } as const;

  const startFormFilterDropdownProps: React.ComponentProps<
    typeof FilterDropdown
  > = {
    items: START_FORM_FILTER_OPTIONS,
    selected: startFormFilterValue,
    onChange: onStartFormFilterChange,
  } as const;

  return (
    <div className={styles.container}>
      <Stack className={styles.content} gap={2}>
        <div className={styles.searchContainer}>
          <Stack className={styles.searchContainerInner} gap={6}>
            <Grid narrow>
              <Column sm={4} md={8} lg={16}>
                <Stack gap={4}>
                  <h1>{t('headerNavItemProcesses')}</h1>
                  <p>{t('processesSubtitle')}</p>
                </Stack>
              </Column>
            </Grid>
            {isMultitenancyEnabled ? (
              <Grid narrow>
                <Column
                  className={styles.searchFieldWrapper}
                  sm={4}
                  md={8}
                  lg={10}
                >
                  <Search {...processSearchProps} />
                </Column>
                <Column
                  className={styles.searchFieldWrapper}
                  sm={2}
                  md={4}
                  lg={3}
                >
                  <FilterDropdown {...startFormFilterDropdownProps} />
                </Column>
                <Column
                  className={styles.searchFieldWrapper}
                  sm={2}
                  md={4}
                  lg={2}
                >
                  <MultitenancyDropdown
                    initialSelectedItem={initialTenant}
                    onChange={onTenantChange}
                  />
                </Column>
              </Grid>
            ) : (
              <Grid narrow>
                <Column
                  className={styles.searchFieldWrapper}
                  sm={4}
                  md={5}
                  lg={10}
                >
                  <Search {...processSearchProps} />
                </Column>
                <Column
                  className={styles.searchFieldWrapper}
                  sm={4}
                  md={3}
                  lg={5}
                >
                  <FilterDropdown {...startFormFilterDropdownProps} />
                </Column>
              </Grid>
            )}
          </Stack>
        </div>

        <div className={styles.processTilesContainer}>
          <div className={styles.processTilesContainerInner}>
            {!isLoading && processes.length === 0 ? (
              <Layer>
                <C3EmptyState
                  icon={
                    isFiltered
                      ? undefined
                      : {path: EmptyMessageImage, altText: ''}
                  }
                  heading={
                    isFiltered
                      ? t('processesProcessNotFoundError')
                      : t('processesProcessNotPublishedError')
                  }
                  description={
                    <span data-testid="empty-message">
                      {t('processesErrorBody')}
                      <Link
                        href="https://docs.camunda.io/docs/components/modeler/web-modeler/run-or-publish-your-process/#publishing-a-process"
                        target="_blank"
                        rel="noopener noreferrer"
                        inline
                        onClick={() => {
                          tracking.track({
                            eventName: 'processes-empty-message-link-clicked',
                          });
                        }}
                      >
                        {t('processesErrorBodyLinkLabel')}
                      </Link>
                    </span>
                  }
                />
              </Layer>
            ) : (
              <Grid narrow as={Layer}>
                {isLoading
                  ? Array.from({length: 5}).map((_, index) => (
                      <Column
                        className={styles.processTileWrapper}
                        sm={4}
                        md={4}
                        lg={5}
                        key={index}
                      >
                        <SkeletonPlaceholder
                          className={styles.tileSkeleton}
                          data-testid="process-skeleton"
                        />
                      </Column>
                    ))
                  : processes.map((process, idx) => (
                      <Column
                        className={styles.processTileWrapper}
                        sm={4}
                        md={4}
                        lg={5}
                        key={getUniqueId(process)}
                      >
                        <ProcessTile
                          process={process}
                          displayName={getMultiModeProcessDisplayName(process)}
                          isFirst={idx === 0}
                          isStartButtonDisabled={isStartButtonDisabled}
                          data-testid="process-tile"
                          tenantId={selectedTenantId}
                          onStartProcess={onStartProcess(process)}
                          onStartProcessError={onStartProcessError(process)}
                          onStartProcessSuccess={onStartProcessSuccess}
                          status={getStartProcessStatus(process)}
                        />
                      </Column>
                    ))}
              </Grid>
            )}
          </div>
        </div>
      </Stack>
    </div>
  );
}

export {ProcessesList};
