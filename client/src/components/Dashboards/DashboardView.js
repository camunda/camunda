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
import {checkDeleteConflict, evaluateReport} from 'services';

import {
  Button,
  ShareEntity,
  DashboardRenderer,
  Icon,
  Dropdown,
  Popover,
  ConfirmationModal
} from 'components';

import {themed} from 'theme';
import {t} from 'translation';

import {getSharedDashboard, shareDashboard, revokeDashboardSharing} from './service';

import {DimensionSetter} from './DimensionSetter';
import {AutoRefreshBehavior, AutoRefreshIcon} from './AutoRefresh';

import './DashboardView.scss';

export default themed(
  class DashboardView extends React.Component {
    state = {
      fullScreenActive: false,
      autoRefreshInterval: null,
      confirmModalVisible: false,
      conflicts: [],
      deleteLoading: false
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

    showDeleteModal = async () => {
      this.setState({
        confirmModalVisible: true,
        deleteLoading: true
      });
      const {conflictedItems} = await checkDeleteConflict(this.props.id, 'dashboard');
      this.setState({conflicts: conflictedItems, deleteLoading: false});
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

    closeConfirmModal = () => {
      this.setState({
        confirmModalVisible: false
      });
    };

    render() {
      const {
        id,
        name,
        lastModifier,
        lastModified,
        isAuthorizedToShare,
        sharingEnabled,
        reports
      } = this.props;

      const {
        fullScreenActive,
        autoRefreshInterval,
        confirmModalVisible,
        conflicts,
        deleteLoading
      } = this.state;

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
                      <Button onClick={this.showDeleteModal} className="tool-button delete-button">
                        <Icon type="delete" />
                        {t('common.delete')}
                      </Button>
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
                    <Button onClick={this.props.toggleTheme} className="tool-button theme-toggle">
                      {t('dashboard.toggleTheme')}
                    </Button>
                  )}
                  <Button
                    onClick={() => this.changeFullScreen(!this.state.fullScreenActive)}
                    className="tool-button fullscreen-button"
                  >
                    <Icon type={fullScreenActive ? 'exit-fullscreen' : 'fullscreen'} />{' '}
                    {t(
                      fullScreenActive ? 'dashboard.leaveFullscreen' : 'dashboard.enterFullscreen'
                    )}
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
            <ConfirmationModal
              open={confirmModalVisible}
              onClose={this.closeConfirmModal}
              onConfirm={this.props.deleteDashboard}
              conflict={{type: 'delete', items: conflicts}}
              entityName={name}
              loading={deleteLoading}
            />
            <DashboardRenderer
              loadReport={evaluateReport}
              reports={reports}
              reportAddons={
                autoRefreshInterval && [
                  <AutoRefreshBehavior key="autorefresh" interval={autoRefreshInterval} />
                ]
              }
            >
              <DimensionSetter reports={reports} />
            </DashboardRenderer>
          </div>
        </Fullscreen>
      );
    }
  }
);
