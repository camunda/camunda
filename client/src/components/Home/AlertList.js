/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {t} from 'translation';
import {LoadingIndicator, Icon, Dropdown, Input, ConfirmationModal, Button} from 'components';
import {showError} from 'notifications';
import {formatters, loadEntities, isDurationReport} from 'services';
import {withErrorHandling} from 'HOC';

import AlertModal from './AlertModal';
import ListItem from './ListItem';

import {loadAlerts, addAlert, editAlert, removeAlert} from './service';

import {ReactComponent as AlertIcon} from './icons/alert.svg';

import './AlertList.scss';

const {duration, frequency} = formatters;

export default withErrorHandling(
  class AlertList extends React.Component {
    state = {
      deleting: null,
      editing: null,
      deleteInProgress: false,
      reports: null,
      alerts: null,
      searchQuery: ''
    };

    componentDidMount() {
      this.loadData();
    }

    componentDidUpdate(prevProps) {
      if (prevProps.collection !== this.props.collection) {
        this.loadData();
      }
    }

    loadData() {
      this.loadReports();
      this.loadAlerts();
    }

    loadReports = () => {
      this.props.mightFail(
        loadEntities('report'),
        reports =>
          this.setState({
            reports: reports.filter(
              ({combined, collectionId, data: {visualization}}) =>
                !combined && visualization === 'number' && collectionId === this.props.collection
            )
          }),
        error => {
          showError(error);
          this.setState({reports: null});
        }
      );
    };

    loadAlerts = () => {
      this.props.mightFail(loadAlerts(this.props.collection), alerts => {
        this.setState({alerts});
      });
    };

    confirmDelete = entity => {
      this.setState({deleting: entity});
    };

    resetDelete = () => this.setState({deleting: null, deleteInProgress: false});

    deleteAlert = () => {
      const {id} = this.state.deleting;
      this.resetDelete();
      this.setState({deleteInProgress: true});
      this.props.mightFail(
        removeAlert(id),
        () => {
          this.loadAlerts();
          this.setState({deleteInProgress: false});
        },
        error => {
          showError(error);
          this.setState({deleteInProgress: false});
        }
      );
    };

    openAddAlertModal = () => this.setState({editing: {}});
    openEditUserModal = editing => this.setState({editing});

    addAlert = newAlert => {
      this.closeEditAlertModal();
      this.props.mightFail(addAlert(newAlert), this.loadAlerts, showError);
    };

    editAlert = changedAlert => {
      this.closeEditAlertModal();
      this.props.mightFail(
        editAlert(this.state.editing.id, changedAlert),
        this.loadAlerts,
        showError
      );
    };
    closeEditAlertModal = () => this.setState({editing: null});

    render() {
      const {deleting, editing, deleteInProgress, searchQuery, reports} = this.state;
      const {readOnly} = this.props;

      return (
        <div className="AlertList">
          <div className="header">
            <h1>{t('alert.label-plural')}</h1>
            <div className="searchContainer">
              <Icon className="searchIcon" type="search" />
              <Input
                required
                type="text"
                className="searchInput"
                placeholder={t('home.search.name')}
                value={searchQuery}
                onChange={({target: {value}}) => this.setState({searchQuery: value})}
                onClear={() => this.setState({searchQuery: ''})}
              />
            </div>
            {!readOnly && <Button onClick={this.openAddAlertModal}>{t('alert.createNew')}</Button>}
          </div>
          <div className="content">
            <ul>{this.renderList()}</ul>
          </div>
          <ConfirmationModal
            open={deleting}
            onClose={this.resetDelete}
            onConfirm={this.deleteAlert}
            entityName={deleting && deleting.name}
            loading={deleteInProgress}
          />
          {editing && reports && (
            <AlertModal
              initialAlert={editing}
              reports={reports}
              onClose={this.closeEditAlertModal}
              onConfirm={alert => {
                if (editing.id) {
                  this.editAlert(alert);
                } else {
                  this.addAlert(alert);
                }
              }}
            />
          )}
        </div>
      );
    }

    formatDescription = (reportId, operator, value) => {
      const report = this.state.reports.find(({id}) => id === reportId);
      const aboveOrBelow = operator === '<' ? t('common.below') : t('common.above');
      const thresholdValue = isDurationReport(report) ? duration(value) : frequency(value);

      return t('alert.description', {
        name: report.name,
        aboveOrBelow,
        thresholdValue
      });
    };

    renderList() {
      const {readOnly} = this.props;
      const {alerts, reports} = this.state;

      if (alerts === null || reports === null) {
        return <LoadingIndicator />;
      }

      const searchFilteredData = alerts.filter(({name}) =>
        name.toLowerCase().includes(this.state.searchQuery.toLowerCase())
      );

      if (searchFilteredData.length === 0) {
        return <div className="empty">{t('common.notFound')}</div>;
      }

      return searchFilteredData.map(alert => {
        const {id, name, email, reportId, threshold, thresholdOperator} = alert;

        const description = this.formatDescription(reportId, thresholdOperator, threshold);
        return (
          <ListItem key={id} onClick={!readOnly ? () => this.openEditUserModal(alert) : undefined}>
            <ListItem.Section className="icon">
              <AlertIcon />
            </ListItem.Section>
            <ListItem.Section className="name">
              <div className="type">{t('alert.label')}</div>
              <div className="entityName" title={name}>
                {name}
              </div>
            </ListItem.Section>
            <ListItem.Section className="email" title={email}>
              {email}
            </ListItem.Section>
            <ListItem.Section className="condition" title={description}>
              {description}
            </ListItem.Section>
            {!readOnly && (
              <div className="contextMenu" onClick={evt => evt.stopPropagation()}>
                <Dropdown label={<Icon type="overflow-menu-vertical" size="24px" />}>
                  <Dropdown.Option onClick={() => this.openEditUserModal(alert)}>
                    <Icon type="edit" />
                    {t('common.edit')}
                  </Dropdown.Option>
                  <Dropdown.Option onClick={() => this.confirmDelete(alert)}>
                    <Icon type="delete" />
                    {t('common.delete')}
                  </Dropdown.Option>
                </Dropdown>
              </div>
            )}
          </ListItem>
        );
      });
    }
  }
);
