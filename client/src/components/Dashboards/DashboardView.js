/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';
import Fullscreen from 'react-full-screen';
import {Link} from 'react-router-dom';
import {evaluateReport} from 'services';

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
} from 'components';

import {themed} from 'theme';
import {t} from 'translation';

import {getSharedDashboard, shareDashboard, revokeDashboardSharing} from './service';
import {FiltersView} from './filters';

import {AutoRefreshBehavior, AutoRefreshIcon} from './AutoRefresh';

import './DashboardView.scss';

export default themed(
  class DashboardView extends React.Component {
    state = {
      fullScreenActive: false,
      autoRefreshInterval: null,
      deleting: null,
      filtersShown: false,
      filter: [],
    };

    componentWillUnmount = () => {
      if (this.props.theme === 'dark') {
        this.props.toggleTheme();
      }
    };

    changeFullScreen = (fullScreenActive) => {
      if (this.props.theme === 'dark') {
        this.props.toggleTheme();
      }
      this.setState({fullScreenActive});
    };

    setAutorefresh = (timeout) => {
      clearInterval(this.timer);
      if (timeout) {
        this.timer = setInterval(this.props.loadDashboard, timeout);
      }
      this.setState({
        autoRefreshInterval: timeout,
      });
    };

    getShareTooltip = () => {
      if (!this.props.sharingEnabled) {
        return t('common.sharing.disabled');
      }
      if (!this.props.isAuthorizedToShare) {
        return t('dashboard.cannotShare');
      }
      return '';
    };

    autoRefreshOption = (interval, label) => {
      return (
        <Dropdown.Option
          active={this.state.autoRefreshInterval === interval}
          onClick={() => this.setAutorefresh(interval)}
        >
          {label}
        </Dropdown.Option>
      );
    };

    render() {
      const {
        id,
        name,
        currentUserRole,
        isAuthorizedToShare,
        sharingEnabled,
        reports,
        availableFilters,
        toggleTheme,
        onDelete,
      } = this.props;

      const {fullScreenActive, autoRefreshInterval, deleting, filter, filtersShown} = this.state;

      return (
        <Fullscreen enabled={fullScreenActive} onChange={this.changeFullScreen}>
          <div
            className={classnames('DashboardView', {
              fullscreen: fullScreenActive,
            })}
          >
            <div className="header">
              <div className="head">
                <EntityName details={<LastModifiedInfo entity={this.props} />}>{name}</EntityName>
                <div className="tools">
                  {!fullScreenActive && (
                    <React.Fragment>
                      {currentUserRole === 'editor' && (
                        <>
                          <Link
                            className="tool-button edit-button"
                            to="edit"
                            onClick={() => this.setAutorefresh(null)}
                          >
                            <Button main tabIndex="-1">
                              <Icon type="edit" />
                              {t('common.edit')}
                            </Button>
                          </Link>
                          <Button
                            main
                            onClick={() =>
                              this.setState({deleting: {...this.props, entityType: 'dashboard'}})
                            }
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
                        tooltip={this.getShareTooltip()}
                      >
                        <ShareEntity
                          type="dashboard"
                          resourceId={id}
                          shareEntity={shareDashboard}
                          revokeEntitySharing={revokeDashboardSharing}
                          getSharedEntity={getSharedDashboard}
                        />
                      </Popover>
                    </React.Fragment>
                  )}
                  {fullScreenActive && (
                    <Button main onClick={toggleTheme} className="tool-button theme-toggle">
                      {t('dashboard.toggleTheme')}
                    </Button>
                  )}
                  {availableFilters?.length > 0 && (
                    <Button
                      main
                      className="tool-button filter-button"
                      active={filtersShown}
                      onClick={() =>
                        this.setState(({filtersShown}) => {
                          if (filtersShown) {
                            // filters are currently shown. Hide and reset filters
                            return {filtersShown: false, filter: []};
                          } else {
                            // show filter panel
                            return {filtersShown: true};
                          }
                        })
                      }
                    >
                      <Icon type="filter" /> {t('dashboard.filter.label')}
                    </Button>
                  )}

                  <Button
                    main
                    onClick={() => this.changeFullScreen(!fullScreenActive)}
                    className="tool-button fullscreen-button"
                  >
                    <Icon type={fullScreenActive ? 'exit-fullscreen' : 'fullscreen'} />{' '}
                    {fullScreenActive
                      ? t('dashboard.leaveFullscreen')
                      : t('dashboard.enterFullscreen')}
                  </Button>
                  <Dropdown
                    main
                    label={
                      <React.Fragment>
                        <AutoRefreshIcon interval={autoRefreshInterval} />{' '}
                        {t('dashboard.autoRefresh')}
                      </React.Fragment>
                    }
                    active={!!autoRefreshInterval}
                  >
                    {this.autoRefreshOption(null, t('common.off'))}
                    {this.autoRefreshOption(1 * 60 * 1000, '1 ' + t('common.unit.minute.label'))}
                    {this.autoRefreshOption(
                      5 * 60 * 1000,
                      '5 ' + t('common.unit.minute.label-plural')
                    )}
                    {this.autoRefreshOption(
                      10 * 60 * 1000,
                      '10 ' + t('common.unit.minute.label-plural')
                    )}
                    {this.autoRefreshOption(
                      15 * 60 * 1000,
                      '15 ' + t('common.unit.minute.label-plural')
                    )}
                    {this.autoRefreshOption(
                      30 * 60 * 1000,
                      '30 ' + t('common.unit.minute.label-plural')
                    )}
                    {this.autoRefreshOption(
                      60 * 60 * 1000,
                      '60 ' + t('common.unit.minute.label-plural')
                    )}
                  </Dropdown>
                </div>
              </div>
            </div>
            {filtersShown && (
              <FiltersView
                availableFilters={availableFilters}
                filter={filter}
                setFilter={(filter) => this.setState({filter})}
              />
            )}
            <Deleter
              type="dashboard"
              entity={deleting}
              onDelete={onDelete}
              onClose={() => this.setState({deleting: null})}
            />
            <DashboardRenderer
              loadReport={evaluateReport}
              reports={reports}
              filter={filter}
              addons={
                autoRefreshInterval && [
                  <AutoRefreshBehavior key="autorefresh" interval={autoRefreshInterval} />,
                ]
              }
            />
          </div>
        </Fullscreen>
      );
    }
  }
);
