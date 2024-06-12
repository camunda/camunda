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
import {CustomFiltersModal} from './CustomFiltersModal';
import {DeleteFilterModal} from './CustomFiltersModal/DeleteFilterModal';

function getCustomFilterParams(options: {userId: string; filter: string}) {
  const {userId, filter} = options;
  const customFilters = getStateLocally('customFilters') ?? {};
  const filters = customFilters[filter];

  return filters === undefined
    ? {}
    : prepareCustomFiltersParams(filters, userId);
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
  const NON_CUSTOM_FILTERS = [
    'all-open',
    'unassigned',
    'assigned-to-me',
    'completed',
  ];
  const customFilterParams = NON_CUSTOM_FILTERS.includes(filter)
    ? {}
    : getCustomFilterParams({userId, filter});

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
  const [customFilterToEdit, setCustomFilterToEdit] = useState<
    string | undefined
  >();
  const [customFilterToDelete, setCustomFilterToDelete] = useState<
    string | undefined
  >();
  const wasCollapsed = usePrevious(isCollapsed);
  const {filter} = useTaskFilters();
  const [searchParams] = useSearchParams();
  const customFilters = Object.entries(getStateLocally('customFilters') ?? {});
  const {data} = useCurrentUser();
  const userId = data?.userId ?? '';
  const filtersModal = (
    <CustomFiltersModal
      key="custom-filters-modal"
      filterId={customFilterToEdit}
      isOpen={isCustomFiltersModalOpen || customFilterToEdit !== undefined}
      onClose={() => {
        setIsCustomFiltersModalOpen(false);
        setCustomFilterToEdit(undefined);
      }}
      onSuccess={(filter) => {
        setIsCustomFiltersModalOpen(false);
        setCustomFilterToEdit(undefined);
        navigate({
          search: getNavLinkSearchParam({
            currentParams: searchParams,
            filter,
            userId,
          }),
        });
      }}
      onDelete={() => {
        setIsCustomFiltersModalOpen(false);
        setCustomFilterToEdit(undefined);
        navigate({
          search: getNavLinkSearchParam({
            currentParams: searchParams,
            filter: 'all-open',
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
        <div className={styles.scrollContainer}>
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
            {customFilters.map(([filterId, {name}]) => (
              <li className={styles.customFilterContainer} key={filterId}>
                <ControlledNavLink
                  to={{
                    search: getNavLinkSearchParam({
                      currentParams: searchParams,
                      filter: filterId,
                      userId,
                    }),
                  }}
                  isActive={filter === filterId}
                  className={styles.customFilterNav}
                >
                  {filterId === 'custom' || name === undefined
                    ? 'Custom'
                    : name}
                </ControlledNavLink>
                <OverflowMenu
                  iconDescription="Custom filter actions"
                  size="md"
                  className={cn(styles.overflowMenu, {
                    [styles.selected]: filter === filterId,
                  })}
                  direction="top"
                  flipped
                  align="top-right"
                >
                  <OverflowMenuItem
                    itemText="Edit"
                    onClick={() => {
                      setCustomFilterToEdit(filterId);
                    }}
                  />
                  <OverflowMenuItem
                    hasDivider
                    isDelete
                    itemText="Delete"
                    onClick={() => {
                      setCustomFilterToDelete(filterId);
                    }}
                  />
                </OverflowMenu>
              </li>
            ))}
          </ul>
          <ButtonSet>
            <Button
              onClick={() => {
                setIsCustomFiltersModalOpen(true);
              }}
              kind="ghost"
              size="md"
            >
              New filter
            </Button>
          </ButtonSet>
        </div>
      </nav>
      {filtersModal}
      <DeleteFilterModal
        data-testid="direct-delete-filter-modal"
        filterName={customFilterToDelete ?? ''}
        isOpen={customFilterToDelete !== undefined}
        onClose={() => {
          setCustomFilterToDelete(undefined);
        }}
        onDelete={() => {
          navigate({
            search: getNavLinkSearchParam({
              currentParams: searchParams,
              filter: 'all-open',
              userId,
            }),
          });
          setCustomFilterToDelete(undefined);
        }}
      />
    </Layer>
  );
};

export {CollapsiblePanel};
