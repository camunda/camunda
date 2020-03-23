/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {t} from 'translation';
import {Button, EntityList, Deleter} from 'components';
import {showError} from 'notifications';
import {formatters, loadReports, isDurationReport} from 'services';
import {withErrorHandling} from 'HOC';
import {getWebhooks} from 'config';

import AlertModal from './modals/AlertModal';

import {loadAlerts, addAlert, editAlert, removeAlert} from './service';

import './AlertList.scss';

const {duration, frequency} = formatters;

export default withErrorHandling(
  class AlertList extends React.Component {
    state = {
      deleting: null,
      editing: null,
      reports: null,
      alerts: null,
      webhooks: null
    };

    componentDidMount() {
      this.loadData();
      this.loadWebhooks();
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
        loadReports(this.props.collection),
        reports =>
          this.setState({
            reports: reports.filter(
              ({combined, data: {visualization}}) => !combined && visualization === 'number'
            )
          }),
        error => {
          showError(error);
          this.setState({reports: null});
        }
      );
    };

    loadAlerts = () => {
      this.props.mightFail(
        loadAlerts(this.props.collection),
        alerts => {
          this.setState({alerts});
        },
        showError
      );
    };

    loadWebhooks = () => {
      this.props.mightFail(getWebhooks(), webhooks => this.setState({webhooks}), showError);
    };

    openAddAlertModal = () => this.setState({editing: {}});
    openEditAlertModal = editing => this.setState({editing});

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
      const {deleting, editing, alerts, reports, webhooks} = this.state;
      const {readOnly} = this.props;

      const isLoading = alerts === null || reports === null;

      return (
        <div className="AlertList">
          <EntityList
            name={t('alert.label-plural')}
            action={
              !readOnly && (
                <Button main primary onClick={this.openAddAlertModal}>
                  {t('alert.createNew')}
                </Button>
              )
            }
            empty={t('alert.notCreated')}
            isLoading={isLoading}
            columns={[t('common.name'), t('alert.recipient'), t('common.description')]}
            data={
              !isLoading &&
              this.state.alerts.map(alert => {
                const {name, email, webhook, reportId, threshold, thresholdOperator} = alert;
                const inactive = webhook && !email && !webhooks?.includes(webhook);

                return {
                  icon: 'alert',
                  type: t('alert.label'),
                  name,
                  meta: [
                    email || webhook,
                    this.formatDescription(reportId, thresholdOperator, threshold)
                  ],
                  warning: inactive && t('alert.inactiveStatus'),
                  actions: !readOnly && [
                    {
                      icon: 'edit',
                      text: t('common.edit'),
                      action: () => this.openEditAlertModal(alert)
                    },
                    {
                      icon: 'delete',
                      text: t('common.delete'),
                      action: () => this.setState({deleting: alert})
                    }
                  ]
                };
              })
            }
          />
          <Deleter
            type="alert"
            entity={deleting}
            onDelete={this.loadAlerts}
            onClose={() => this.setState({deleting: null})}
            deleteEntity={({id}) => removeAlert(id)}
          />
          {editing && reports && (
            <AlertModal
              initialAlert={editing}
              reports={reports}
              webhooks={webhooks}
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
  }
);
