/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import styles from './styles.module.scss';
import sharedStyles from 'modules/styles/panelHeader.module.scss';
import {
  Button,
  ButtonSet,
  Layer,
  OverflowMenu,
  OverflowMenuItem,
} from '@carbon/react';
import {SidePanelOpen, SidePanelClose, Filter} from '@carbon/react/icons';
import cn from 'classnames';
import {useNavigate, useSearchParams} from 'react-router-dom';
import {useTaskFilters, type TaskFilters} from 'modules/hooks/useTaskFilters';
import {ControlledNavLink} from './ControlledNavLink';
import {prepareCustomFiltersParams} from 'modules/custom-filters/prepareCustomFiltersParams';
import {getStateLocally} from 'modules/utils/localStorage';
import difference from 'lodash/difference';
import {useCurrentUser} from 'modules/queries/useCurrentUser';
import {usePrevious} from '@uidotdev/usehooks';
import {FieldsModal} from './CustomFiltersModal/FieldsModal';
import {DeleteFilterModal} from './CustomFiltersModal/DeleteFilterModal';

function getCustomFilterParams(userId: string) {
  const customFilters = getStateLocally('customFilters')?.custom;
  return customFilters === undefined
    ? {}
    : prepareCustomFiltersParams(customFilters, userId);
}

function getNavLinkSearchParam(options: {
  currentParams: URLSearchParams;
  filter: TaskFilters['filter'];
  userId: string;
}): string {
  const CUSTOM_FILTERS_PARAMS = [
    'state',
    'followUpDateFrom',
    'followUpDateTo',
    'dueDateFrom',
    'dueDateTo',
    'assigned',
    'assignee',
    'taskDefinitionId',
    'candidateGroup',
    'candidateUser',
    'processDefinitionKey',
    'processInstanceKey',
    'tenantIds',
    'taskVariables',
  ] as const;
  const {filter, userId, currentParams} = options;
  const {sortBy, ...convertedParams} = Object.fromEntries(
    currentParams.entries(),
  );
  const customFilterParams =
    filter === 'custom' ? getCustomFilterParams(userId) : {};

  const updatedParams = new URLSearchParams({
    ...convertedParams,
    ...customFilterParams,
    filter,
  });

  if (sortBy !== undefined && sortBy !== 'completion') {
    updatedParams.set('sortBy', sortBy);
  }

  if (filter === 'completed') {
    updatedParams.set('sortBy', 'completion');
  }

  difference(CUSTOM_FILTERS_PARAMS, Object.keys(customFilterParams)).forEach(
    (param) => {
      updatedParams.delete(param);
    },
  );

  return updatedParams.toString();
}

const CollapsiblePanel: React.FC = () => {
  const [isCustomFiltersModalOpen, setIsCustomFiltersModalOpen] =
    useState(false);
  const navigate = useNavigate();
  const [isCollapsed, setIsCollapsed] = useState(true);
  const [customFilterToDelete, setCustomFilterToDelete] = useState<
    string | null
  >(null);
  const wasCollapsed = usePrevious(isCollapsed);
  const {filter} = useTaskFilters();
  const [searchParams] = useSearchParams();
  const customFilters = getStateLocally('customFilters')?.custom;
  const {data} = useCurrentUser();
  const userId = data?.userId ?? '';
  const filtersModal = (
    <FieldsModal
      key="custom-filters-modal"
      isOpen={isCustomFiltersModalOpen}
      onClose={() => {
        setIsCustomFiltersModalOpen(false);
      }}
      onApply={() => {
        setIsCustomFiltersModalOpen(false);
        navigate({
          search: getNavLinkSearchParam({
            currentParams: searchParams,
            filter: 'custom',
            userId,
          }),
        });
      }}
    />
  );

  if (isCollapsed) {
    return (
      <Layer
        as="nav"
        id="task-nav-bar"
        className={cn(styles.base, styles.collapsedContainer)}
        aria-label="Filter controls"
        aria-owns="task-nav-bar-controls"
      >
        <ul id="task-nav-bar-controls" aria-labelledby="task-nav-bar">
          <li>
            <Button
              hasIconOnly
              iconDescription="Expand to show filters"
              tooltipPosition="right"
              onClick={() => {
                setIsCollapsed(false);
              }}
              renderIcon={SidePanelOpen}
              size="md"
              kind="ghost"
              aria-controls="task-nav-bar"
              aria-expanded="false"
              autoFocus={wasCollapsed !== null && !wasCollapsed}
              type="button"
            />
          </li>
          <li>
            <Button
              hasIconOnly
              iconDescription="Filter tasks"
              tooltipPosition="right"
              onClick={() => {
                setIsCustomFiltersModalOpen(true);
              }}
              renderIcon={Filter}
              size="md"
              kind="ghost"
              type="button"
            />
          </li>
        </ul>
        {filtersModal}
      </Layer>
    );
  }

  return (
    <Layer className={styles.floatingContainer}>
      <nav
        aria-labelledby="filters-title"
        className={cn(styles.base, styles.expandedContainer)}
        id="task-nav-bar"
        aria-owns="filters-menu"
      >
        <span className={sharedStyles.panelHeader}>
          <h1 id="filters-title">Filters</h1>
          <Button
            hasIconOnly
            iconDescription="Collapse "
            tooltipPosition="right"
            onClick={() => {
              setIsCollapsed(true);
            }}
            renderIcon={SidePanelClose}
            size="md"
            kind="ghost"
            aria-controls="task-nav-bar"
            aria-expanded="true"
            autoFocus
          />
        </span>
        <ul id="filters-menu" aria-labelledby="task-nav-bar">
          <li>
            <ControlledNavLink
              to={{
                search: getNavLinkSearchParam({
                  currentParams: searchParams,
                  filter: 'all-open',
                  userId,
                }),
              }}
              isActive={filter === 'all-open'}
            >
              All open tasks
            </ControlledNavLink>
          </li>
          <li>
            <ControlledNavLink
              to={{
                search: getNavLinkSearchParam({
                  currentParams: searchParams,
                  filter: 'assigned-to-me',
                  userId,
                }),
              }}
              isActive={filter === 'assigned-to-me'}
            >
              Assigned to me
            </ControlledNavLink>
          </li>
          <li>
            <ControlledNavLink
              to={{
                search: getNavLinkSearchParam({
                  currentParams: searchParams,
                  filter: 'unassigned',
                  userId,
                }),
              }}
              isActive={filter === 'unassigned'}
            >
              Unassigned
            </ControlledNavLink>
          </li>
          <li>
            <ControlledNavLink
              to={{
                search: getNavLinkSearchParam({
                  currentParams: searchParams,
                  filter: 'completed',
                  userId,
                }),
              }}
              isActive={filter === 'completed'}
            >
              Completed
            </ControlledNavLink>
          </li>
          {customFilters === undefined ? null : (
            <li className={styles.customFilterContainer}>
              <ControlledNavLink
                to={{
                  search: getNavLinkSearchParam({
                    currentParams: searchParams,
                    filter: 'custom',
                    userId,
                  }),
                }}
                isActive={filter === 'custom'}
                className={styles.customFilterNav}
              >
                Custom
              </ControlledNavLink>
              <OverflowMenu
                iconDescription="Custom filter actions"
                size="lg"
                className={cn(styles.overflowMenu, {
                  [styles.selected]: filter === 'custom',
                })}
              >
                <OverflowMenuItem
                  itemText="Edit"
                  onClick={() => {
                    setIsCustomFiltersModalOpen(true);
                  }}
                />
                <OverflowMenuItem
                  hasDivider
                  isDelete
                  itemText="Delete"
                  onClick={() => {
                    setCustomFilterToDelete('custom');
                  }}
                />
              </OverflowMenu>
            </li>
          )}
          <ButtonSet>
            <Button
              onClick={() => {
                setIsCustomFiltersModalOpen(true);
              }}
              kind="ghost"
            >
              New filter
            </Button>
          </ButtonSet>
        </ul>
      </nav>
      {filtersModal}
      <DeleteFilterModal
        data-testid="direct-delete-filter-modal"
        filterName={customFilterToDelete ?? ''}
        isOpen={customFilterToDelete !== undefined}
        onClose={() => {
          setCustomFilterToDelete(null);
        }}
        onDelete={() => {
          navigate({
            search: getNavLinkSearchParam({
              currentParams: searchParams,
              filter: 'all-open',
              userId,
            }),
          });
          setCustomFilterToDelete(null);
        }}
      />
    </Layer>
  );
};

export {CollapsiblePanel};
