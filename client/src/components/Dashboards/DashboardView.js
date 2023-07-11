/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState, useEffect, useRef} from 'react';
import classnames from 'classnames';
import {FullScreen, useFullScreenHandle} from 'react-full-screen';
import {Link} from 'react-router-dom';

import {
  Button,
  ShareEntity,
  DashboardRenderer,
  LastModifiedInfo,
  Icon,
  Popover,
  Deleter,
  EntityName,
  DiagramScrollLock,
  AlertsDropdown,
  EntityDescription,
} from 'components';
import {evaluateReport} from 'services';
import {themed} from 'theme';
import {t} from 'translation';
import {getOptimizeProfile} from 'config';

import {
  getSharedDashboard,
  shareDashboard,
  revokeDashboardSharing,
  getDefaultFilter,
} from './service';
import {FiltersView} from './filters';

import {AutoRefreshBehavior, AutoRefreshSelect} from './AutoRefresh';

import './DashboardView.scss';

export function DashboardView(props) {
  const {
    id,
    name,
    description,
    currentUserRole,
    isAuthorizedToShare,
    sharingHidden,
    sharingEnabled,
    tiles,
    availableFilters,
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
    simplifiedDateFilter,
  } = props;
  const [autoRefreshInterval, setAutoRefreshInterval] = useState(refreshRateSeconds * 1000);
  const [deleting, setDeleting] = useState(null);
  const [filtersShown, setFiltersShown] = useState(availableFilters?.length > 0);
  const [filter, setFilter] = useState(getDefaultFilter(availableFilters));
  const fullScreenHandle = useFullScreenHandle();
  const [optimizeProfile, setOptimizeProfile] = useState();

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

  useEffect(() => {
    (async () => {
      setOptimizeProfile(await getOptimizeProfile());
    })();
  }, []);

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
              >
                {name}
              </EntityName>
              {description && <EntityDescription description={description} />}
            </div>
            <div className="tools">
              {!fullScreenHandle.active && (
                <React.Fragment>
                  {currentUserRole === 'editor' && (
                    <>
                      <Link className="tool-button edit-button" to="edit">
                        <Button main tabIndex="-1">
                          <Icon type="edit" />
                          {t('common.edit')}
                        </Button>
                      </Link>
                      <Button
                        main
                        onClick={() => setDeleting({...props, entityType: 'dashboard'})}
                        className="tool-button delete-button"
                      >
                        <Icon type="delete" />
                        {t('common.delete')}
                      </Button>
                    </>
                  )}
                  {!sharingHidden && (
                    <Popover
                      main
                      className="tool-button share-button"
                      icon="share"
                      title={t('common.sharing.buttonTitle')}
                      disabled={!sharingEnabled || !isAuthorizedToShare}
                      tooltip={getShareTooltip()}
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
                </React.Fragment>
              )}
              {fullScreenHandle.active && (
                <Button main onClick={toggleTheme} className="tool-button theme-toggle">
                  {t('dashboard.toggleTheme')}
                </Button>
              )}
              {availableFilters?.length > 0 && (
                <Button
                  main
                  className="tool-button filter-button"
                  active={filtersShown}
                  onClick={() => {
                    if (filtersShown) {
                      setFiltersShown(false);
                      setFilter([]);
                    } else {
                      setFiltersShown(true);
                      setFilter(getDefaultFilter(availableFilters));
                    }
                  }}
                >
                  <Icon type="filter" /> {t('dashboard.filter.viewButtonText')}
                </Button>
              )}
              {!fullScreenHandle.active &&
                (optimizeProfile === 'cloud' || optimizeProfile === 'platform') && (
                  <AlertsDropdown dashboardReports={tiles} />
                )}
              <Button
                main
                onClick={() =>
                  fullScreenHandle.active ? fullScreenHandle.exit() : fullScreenHandle.enter()
                }
                className="tool-button fullscreen-button"
              >
                <Icon type={fullScreenHandle.active ? 'exit-fullscreen' : 'fullscreen'} />{' '}
                {fullScreenHandle.active
                  ? t('dashboard.leaveFullscreen')
                  : t('dashboard.enterFullscreen')}
              </Button>
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
            simplifiedDateFilter={simplifiedDateFilter}
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
            loadReport={evaluateReport}
            tiles={tiles}
            filter={filter}
            disableNameLink={disableNameLink}
            customizeReportLink={customizeReportLink}
            addons={[
              <AutoRefreshBehavior key="autorefresh" interval={autoRefreshInterval} />,
              <DiagramScrollLock key="diagramScrollLock" />,
            ]}
          />
        </div>
      </div>
    </FullScreen>
  );
}

export default themed(DashboardView);
