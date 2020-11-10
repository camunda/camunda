/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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
  Dropdown,
  Popover,
  Deleter,
  EntityName,
  DiagramScrollLock,
} from 'components';
import {evaluateReport} from 'services';
import {themed} from 'theme';
import {t} from 'translation';

import {getSharedDashboard, shareDashboard, revokeDashboardSharing} from './service';
import {FiltersView} from './filters';

import {AutoRefreshBehavior, AutoRefreshIcon} from './AutoRefresh';

import './DashboardView.scss';

export function DashboardView(props) {
  const {
    id,
    name,
    currentUserRole,
    isAuthorizedToShare,
    sharingEnabled,
    reports,
    availableFilters,
    theme,
    toggleTheme,
    lastModified,
    lastModifier,
    owner,
    loadDashboard,
    onDelete,
  } = props;

  const [autoRefreshInterval, setAutoRefreshInterval] = useState(null);
  const [autoRefreshHandle, setAutoRefreshHandle] = useState();
  const [deleting, setDeleting] = useState(null);
  const [filtersShown, setFiltersShown] = useState(availableFilters?.length > 0);
  const [filter, setFilter] = useState([]);
  const fullScreenHandle = useFullScreenHandle();

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

  function setAutorefresh(timeout) {
    clearInterval(autoRefreshHandle);
    if (timeout) {
      setAutoRefreshHandle(setInterval(loadDashboard, timeout));
    }
    setAutoRefreshInterval(timeout);
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

  function autoRefreshOption(interval, label) {
    return (
      <Dropdown.Option
        active={autoRefreshInterval === interval}
        onClick={() => setAutorefresh(interval)}
      >
        {label}
      </Dropdown.Option>
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
            <EntityName details={<LastModifiedInfo entity={{lastModified, lastModifier, owner}} />}>
              {name}
            </EntityName>
            <div className="tools">
              {!fullScreenHandle.active && (
                <React.Fragment>
                  {currentUserRole === 'editor' && (
                    <>
                      <Link
                        className="tool-button edit-button"
                        to="edit"
                        onClick={() => setAutorefresh(null)}
                      >
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
                    />
                  </Popover>
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
                    }
                  }}
                >
                  <Icon type="filter" /> {t('dashboard.filter.viewButtonText')}
                </Button>
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
              <Dropdown
                main
                label={
                  <React.Fragment>
                    <AutoRefreshIcon interval={autoRefreshInterval} /> {t('dashboard.autoRefresh')}
                  </React.Fragment>
                }
                active={!!autoRefreshInterval}
              >
                {autoRefreshOption(null, t('common.off'))}
                {autoRefreshOption(1 * 60 * 1000, '1 ' + t('common.unit.minute.label'))}
                {autoRefreshOption(5 * 60 * 1000, '5 ' + t('common.unit.minute.label-plural'))}
                {autoRefreshOption(10 * 60 * 1000, '10 ' + t('common.unit.minute.label-plural'))}
                {autoRefreshOption(15 * 60 * 1000, '15 ' + t('common.unit.minute.label-plural'))}
                {autoRefreshOption(30 * 60 * 1000, '30 ' + t('common.unit.minute.label-plural'))}
                {autoRefreshOption(60 * 60 * 1000, '60 ' + t('common.unit.minute.label-plural'))}
              </Dropdown>
            </div>
          </div>
        </div>
        {filtersShown && (
          <FiltersView
            reports={reports}
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
            loadReport={evaluateReport}
            reports={reports}
            filter={filter}
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
