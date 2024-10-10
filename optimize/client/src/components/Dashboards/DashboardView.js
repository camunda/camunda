/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useEffect, useRef} from 'react';
import classnames from 'classnames';
import {FullScreen, useFullScreenHandle} from 'react-full-screen';
import {Link, useHistory} from 'react-router-dom';
import {Button} from '@carbon/react';
import {Edit, Filter, Maximize, Minimize, Share, TrashCan} from '@carbon/icons-react';

import {
  ShareEntity,
  DashboardRenderer,
  LastModifiedInfo,
  Popover,
  Deleter,
  EntityName,
  DiagramScrollLock,
  AlertsDropdown,
  EntityDescription,
  DashboardTemplateModal,
} from 'components';
import {evaluateReport, createEntity, deleteEntity, addSources, loadEntities} from 'services';
import {themed} from 'theme';
import {t} from 'translation';
import {showError} from 'notifications';
import {useErrorHandling, useUiConfig, useUser} from 'hooks';

import {
  getSharedDashboard,
  shareDashboard,
  revokeDashboardSharing,
  getDefaultFilter,
} from './service';
import {FiltersView} from './filters';
import {AutoRefreshBehavior, AutoRefreshSelect} from './AutoRefresh';
import useReportDefinitions from './useReportDefinitions';
import DashboardCopyIcon from './DashboardCopyIcon.svg';

import './DashboardView.scss';

export function DashboardView(props) {
  const {
    id,
    name,
    description,
    currentUserRole,
    isAuthorizedToShare,
    isInstantDashboard,
    sharingEnabled,
    tiles,
    availableFilters = [],
    theme,
    toggleTheme,
    lastModified,
    lastModifier,
    owner,
    loadDashboard,
    onDelete,
    refreshRateSeconds,
    disableNameLink,
    customizeReportLink,
  } = props;
  const [autoRefreshInterval, setAutoRefreshInterval] = useState(refreshRateSeconds * 1000);
  const [deleting, setDeleting] = useState(null);
  const [filtersShown, setFiltersShown] = useState(availableFilters?.length > 0);
  const [filter, setFilter] = useState(getDefaultFilter(availableFilters));
  const fullScreenHandle = useFullScreenHandle();
  const [isTemplateModalOpen, setIsTemplateModalOpen] = useState(false);
  const {userSearchAvailable} = useUiConfig();

  const optimizeReports = tiles?.filter(({id, report}) => !!id || !!report);
  const {definitions} = useReportDefinitions(optimizeReports?.[0]);
  const {mightFail} = useErrorHandling();
  const history = useHistory();
  const {user} = useUser();

  const themeRef = useRef(theme);

  // we need to store the theme in a ref in order to access the latest state
  // in the componentDidUnmount effect below
  useEffect(() => {
    themeRef.current = theme;
  }, [theme]);

  useEffect(
    () => () => {
      if (themeRef.current === 'dark') {
        toggleTheme();
      }
    },
    [toggleTheme]
  );

  function changeFullScreen() {
    if (theme === 'dark') {
      toggleTheme();
    }
  }

  function getShareTooltip() {
    if (!sharingEnabled) {
      return t('common.sharing.disabled');
    }
    if (!isAuthorizedToShare) {
      return t('dashboard.cannotShare');
    }
    return '';
  }

  async function handleInstantPreviewDashboardCopying(dashboardState) {
    const {definitions} = dashboardState;
    const [definition] = definitions || [];
    const {displayName, name, key} = definition || {};
    const collectionName =
      definitions.length === 1 ? displayName || name || key : dashboardState.name;
    let collectionId, existingCollection;

    mightFail(
      (async () => {
        const entities = await loadEntities();
        existingCollection = entities.find((entity) => entity.name === collectionName);

        if (existingCollection && existingCollection?.owner === user?.name) {
          collectionId = existingCollection.id;
        } else {
          collectionId = await createEntity('collection', {name: collectionName});
        }
        await addSources(
          collectionId,
          definitions.map((def) => ({
            definitionKey: def.key,
            definitionType: 'process',
            tenants: def.tenantIds,
          }))
        );
      })(),
      () =>
        history.push({
          pathname: '/collection/' + collectionId + '/dashboard/new/edit',
          state: dashboardState,
        }),
      (error) => {
        if (collectionId && !existingCollection) {
          deleteEntity('collection', collectionId);
        }
        showError(error);
      }
    );
  }

  return (
    <FullScreen handle={fullScreenHandle} onChange={changeFullScreen}>
      <div
        className={classnames('DashboardView', {
          fullscreen: fullScreenHandle.active,
        })}
      >
        <div className="header">
          <div className="head">
            <div className="info">
              <EntityName
                details={<LastModifiedInfo entity={{lastModified, lastModifier, owner}} />}
                name={name}
              />
              {description && <EntityDescription description={description} />}
            </div>
            <div className="tools">
              {!fullScreenHandle.active && (
                <>
                  {isInstantDashboard && (
                    <Button
                      kind="primary"
                      className="create-copy"
                      hasIconOnly
                      renderIcon={() => <DashboardCopyIcon />}
                      iconDescription={t('dashboard.copyInstantDashboard')}
                      onClick={() => setIsTemplateModalOpen(true)}
                    />
                  )}
                  {currentUserRole === 'editor' && (
                    <>
                      <Button
                        className="edit-button"
                        as={Link}
                        to="edit"
                        hasIconOnly
                        renderIcon={Edit}
                        iconDescription={t('common.edit')}
                      />
                      <Button
                        kind="ghost"
                        hasIconOnly
                        renderIcon={TrashCan}
                        iconDescription={t('common.delete')}
                        onClick={() => setDeleting({...props, entityType: 'dashboard'})}
                        className="delete-button"
                      />
                    </>
                  )}
                  {!isInstantDashboard && (
                    <Popover
                      isTabTip
                      className="share-button"
                      align="bottom-right"
                      tooltip={getShareTooltip()}
                      trigger={
                        <Popover.Button
                          hasIconOnly
                          iconDescription={t('common.sharing.buttonTitle')}
                          renderIcon={Share}
                          disabled={!sharingEnabled || !isAuthorizedToShare}
                        />
                      }
                    >
                      <ShareEntity
                        type="dashboard"
                        resourceId={id}
                        shareEntity={shareDashboard}
                        revokeEntitySharing={revokeDashboardSharing}
                        getSharedEntity={getSharedDashboard}
                        filter={filter}
                        defaultFilter={getDefaultFilter(availableFilters)}
                      />
                    </Popover>
                  )}
                </>
              )}
              {fullScreenHandle.active && (
                <Button kind="ghost" onClick={toggleTheme} className="theme-toggle">
                  {t('dashboard.toggleTheme')}
                </Button>
              )}
              {availableFilters?.length > 0 && (
                <Button
                  kind="ghost"
                  className="filter-button"
                  isSelected={filtersShown}
                  onClick={() => {
                    if (filtersShown) {
                      setFiltersShown(false);
                      setFilter([]);
                    } else {
                      setFiltersShown(true);
                      setFilter(getDefaultFilter(availableFilters));
                    }
                  }}
                  hasIconOnly
                  iconDescription={t('dashboard.filter.viewButtonText').toString()}
                  renderIcon={Filter}
                />
              )}
              {!fullScreenHandle.active && userSearchAvailable && (
                <AlertsDropdown dashboardTiles={tiles} />
              )}
              <Button
                kind="ghost"
                hasIconOnly
                renderIcon={fullScreenHandle.active ? Minimize : Maximize}
                iconDescription={
                  fullScreenHandle.active
                    ? t('dashboard.leaveFullscreen')
                    : t('dashboard.enterFullscreen')
                }
                onClick={() =>
                  fullScreenHandle.active ? fullScreenHandle.exit() : fullScreenHandle.enter()
                }
                className="fullscreen-button"
              />
              <AutoRefreshSelect
                refreshRateMs={autoRefreshInterval}
                onChange={setAutoRefreshInterval}
                onRefresh={loadDashboard}
              />
            </div>
          </div>
        </div>
        {filtersShown && (
          <FiltersView
            reports={tiles}
            availableFilters={availableFilters}
            filter={filter}
            setFilter={setFilter}
          />
        )}
        <Deleter
          type="dashboard"
          entity={deleting}
          onDelete={onDelete}
          onClose={() => setDeleting(null)}
        />
        <div className="content">
          <DashboardRenderer
            loadTile={evaluateReport}
            tiles={tiles}
            filter={filter}
            disableNameLink={disableNameLink}
            customizeTileLink={customizeReportLink}
            addons={[<AutoRefreshBehavior interval={autoRefreshInterval} />, <DiagramScrollLock />]}
          />
        </div>
      </div>
      {isInstantDashboard && isTemplateModalOpen && (
        <DashboardTemplateModal
          trackingEventName="useInstantPreviewDashboardTemplate"
          initialDefinitions={definitions}
          onClose={() => setIsTemplateModalOpen(false)}
          onConfirm={handleInstantPreviewDashboardCopying}
        />
      )}
    </FullScreen>
  );
}

export default themed(DashboardView);
