/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import styles from './styles.module.scss';
import sharedStyles from 'common/tasks/details/panelHeader.module.scss';
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
import {
  useMultiModeTaskFilters,
  type MultiModeTaskFilters,
} from 'common/tasks/filters/useMultiModeTaskFilters';
import {ControlledNavLink} from './ControlledNavLink';
import {prepareCustomFiltersParams} from 'common/tasks/filters/prepareCustomFiltersParams';
import {getStateLocally} from 'common/local-storage';
import difference from 'lodash/difference';
import {useCurrentUser} from 'common/api/useCurrentUser.query';
import {usePrevious} from '@uidotdev/usehooks';
import {CustomFiltersModal} from './CustomFiltersModal';
import {DeleteFilterModal} from './CustomFiltersModal/DeleteFilterModal';
import {useTranslation} from 'react-i18next';

function getCustomFilterParams(options: {username: string; filter: string}) {
  const {username, filter} = options;
  const customFilters = getStateLocally('customFilters') ?? {};
  const filters = customFilters[filter];

  return filters === undefined
    ? {}
    : prepareCustomFiltersParams(filters, username);
}

function getNavLinkSearchParam(options: {
  currentParams: URLSearchParams;
  filter: MultiModeTaskFilters['filter'];
  username: string;
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
    'elementId',
    'candidateGroup',
    'candidateUser',
    'processDefinitionKey',
    'processInstanceKey',
    'tenantIds',
    'taskVariables',
  ] as const;
  const {filter, username, currentParams} = options;
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
    : getCustomFilterParams({username, filter});

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
  const {t} = useTranslation();
  const [isCollapsed, setIsCollapsed] = useState(true);
  const [customFilterToEdit, setCustomFilterToEdit] = useState<
    string | undefined
  >();
  const [customFilterToDelete, setCustomFilterToDelete] = useState<
    string | undefined
  >();
  const wasCollapsed = usePrevious(isCollapsed);
  const {filter} = useMultiModeTaskFilters();
  const [searchParams] = useSearchParams();
  const customFilters = Object.entries(getStateLocally('customFilters') ?? {});
  const {data} = useCurrentUser();
  const username = data?.username ?? '';
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
            username,
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
            username,
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
        aria-label={t('taskFilterPanelControlsAria')}
        aria-owns="task-nav-bar-controls"
      >
        <ul id="task-nav-bar-controls" aria-labelledby="task-nav-bar">
          <li>
            <Button
              hasIconOnly
              iconDescription={t('taskFilterPanelExpandButton')}
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
              iconDescription={t('taskFilterPanelFilterButton')}
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
          <h1 id="filters-title">{t('taskFilterPanelTitle')}</h1>
          <Button
            hasIconOnly
            iconDescription={t('taskFilterPanelCollapse')}
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
                    username,
                  }),
                }}
                isActive={filter === 'all-open'}
              >
                {t('taskFilterPanelAllOpenTasks')}
              </ControlledNavLink>
            </li>
            <li>
              <ControlledNavLink
                to={{
                  search: getNavLinkSearchParam({
                    currentParams: searchParams,
                    filter: 'assigned-to-me',
                    username,
                  }),
                }}
                isActive={filter === 'assigned-to-me'}
              >
                {t('assigneeTagAssignedToMe')}
              </ControlledNavLink>
            </li>
            <li>
              <ControlledNavLink
                to={{
                  search: getNavLinkSearchParam({
                    currentParams: searchParams,
                    filter: 'unassigned',
                    username,
                  }),
                }}
                isActive={filter === 'unassigned'}
              >
                {t('taskFilterPanelUnassigned')}
              </ControlledNavLink>
            </li>
            <li>
              <ControlledNavLink
                to={{
                  search: getNavLinkSearchParam({
                    currentParams: searchParams,
                    filter: 'completed',
                    username,
                  }),
                }}
                isActive={filter === 'completed'}
              >
                {t('taskFilterPanelCompleted')}
              </ControlledNavLink>
            </li>
            {customFilters.map(([filterId, {name}]) => (
              <li className={styles.customFilterContainer} key={filterId}>
                <ControlledNavLink
                  to={{
                    search: getNavLinkSearchParam({
                      currentParams: searchParams,
                      filter: filterId,
                      username,
                    }),
                  }}
                  isActive={filter === filterId}
                  className={styles.customFilterNav}
                >
                  {filterId === 'custom' || name === undefined
                    ? t('taskFilterPanelCustom')
                    : name}
                </ControlledNavLink>
                <OverflowMenu
                  iconDescription={t('taskFilterPanelCustomFilterActions')}
                  size="md"
                  className={cn(styles.overflowMenu, {
                    [styles.selected]: filter === filterId,
                  })}
                  direction="top"
                  flipped
                  align="top-end"
                >
                  <OverflowMenuItem
                    itemText={t('taskFilterPanelEdit')}
                    onClick={() => {
                      setCustomFilterToEdit(filterId);
                    }}
                  />
                  <OverflowMenuItem
                    hasDivider
                    isDelete
                    itemText={t('taskFilterPanelDelete')}
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
              {t('taskFilterPanelNewFilter')}
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
              username,
            }),
          });
          setCustomFilterToDelete(undefined);
        }}
      />
    </Layer>
  );
};

export {CollapsiblePanel};
