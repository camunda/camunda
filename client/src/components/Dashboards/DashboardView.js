/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import moment from 'moment';
import classnames from 'classnames';
import Fullscreen from 'react-full-screen';
import {Link} from 'react-router-dom';
import {evaluateReport} from 'services';

import {Button, ShareEntity, DashboardRenderer, Icon, Dropdown, Popover, Deleter} from 'components';

import {themed} from 'theme';
import {t} from 'translation';

import {getSharedDashboard, shareDashboard, revokeDashboardSharing} from './service';

import {AutoRefreshBehavior, AutoRefreshIcon} from './AutoRefresh';

import './DashboardView.scss';

export default themed(
  class DashboardView extends React.Component {
    state = {
      fullScreenActive: false,
      autoRefreshInterval: null,
      deleting: null
    };

    componentWillUnmount = () => {
      if (this.props.theme === 'dark') {
        this.props.toggleTheme();
      }
    };

    changeFullScreen = fullScreenActive => {
      if (this.props.theme === 'dark') {
        this.props.toggleTheme();
      }
      this.setState({fullScreenActive});
    };

    setAutorefresh = timeout => {
      clearInterval(this.timer);
      if (timeout) {
        this.timer = setInterval(this.props.loadDashboard, timeout);
      }
      this.setState({
        autoRefreshInterval: timeout
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
        lastModifier,
        lastModified,
        currentUserRole,
        isAuthorizedToShare,
        sharingEnabled,
        reports,
        toggleTheme,
        onDelete
      } = this.props;

      const {fullScreenActive, autoRefreshInterval, deleting} = this.state;

      return (
        <Fullscreen enabled={fullScreenActive} onChange={this.changeFullScreen}>
          <div
            className={classnames('DashboardView', {
              fullscreen: fullScreenActive
            })}
          >
            <div className="header">
              <div className="head">
                <div className="name-container">
                  <h1 className="name">{name}</h1>
                </div>
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
                            <Button>
                              <Icon type="edit" />
                              {t('common.edit')}
                            </Button>
                          </Link>
                          <Button
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
                    <Button onClick={toggleTheme} className="tool-button theme-toggle">
                      {t('dashboard.toggleTheme')}
                    </Button>
                  )}
                  <Button
                    onClick={() => this.changeFullScreen(!fullScreenActive)}
                    className="tool-button fullscreen-button"
                  >
                    <Icon type={fullScreenActive ? 'exit-fullscreen' : 'fullscreen'} />{' '}
                    {fullScreenActive
                      ? t('dashboard.leaveFullscreen')
                      : t('dashboard.enterFullscreen')}
                  </Button>
                  <Dropdown
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
              <div className="subHead">
                <div className="metadata">
                  {t('common.entity.modified')} {moment(lastModified).format('lll')}{' '}
                  {t('common.entity.by')} {lastModifier}
                </div>
              </div>
            </div>
            <Deleter
              type="dashboard"
              entity={deleting}
              onDelete={onDelete}
              onClose={() => this.setState({deleting: null})}
            />
            <DashboardRenderer
              loadReport={evaluateReport}
              reports={reports}
              addons={
                autoRefreshInterval && [
                  <AutoRefreshBehavior key="autorefresh" interval={autoRefreshInterval} />
                ]
              }
            />
          </div>
        </Fullscreen>
      );
    }
  }
);
