/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {t} from 'translation';
import {Button, EntityList, Deleter, BulkDeleter, AlertModal} from 'components';
import {showError} from 'notifications';
import {
  loadAlerts,
  addAlert,
  editAlert,
  removeAlert,
  formatters,
  loadReports,
  isDurationReport,
  isAlertCompatibleReport,
} from 'services';
import {withErrorHandling} from 'HOC';
import {getWebhooks} from 'config';

import CopyAlertModal from './modals/CopyAlertModal';
import {removeAlerts} from './service';

import './AlertList.scss';

const {duration, frequency} = formatters;

export default withErrorHandling(
  class AlertList extends React.Component {
    state = {
      deleting: null,
      editing: null,
      copying: null,
      reports: null,
      alerts: null,
      webhooks: null,
      loading: false,
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
        (reports) =>
          this.setState({
            reports: reports.filter(isAlertCompatibleReport),
          }),
        (error) => {
          showError(error);
          this.setState({reports: null});
        }
      );
    };

    loadAlerts = () => {
      this.props.mightFail(
        loadAlerts(this.props.collection),
        (alerts) => {
          this.setState({alerts});
        },
        showError
      );
    };

    loadWebhooks = () => {
      this.props.mightFail(getWebhooks(), (webhooks) => this.setState({webhooks}), showError);
    };

    openAddAlertModal = () => this.setState({editing: {}});
    openEditAlertModal = (editing) => this.setState({editing});

    addAlert = async (newAlert) => {
      this.setState({loading: true});
      await this.props.mightFail(
        addAlert(newAlert),
        () => {
          this.closeEditAlertModal();
          this.loadAlerts();
        },
        showError
      );
      this.setState({loading: false});
    };

    editAlert = async (changedAlert) => {
      this.setState({loading: true});
      await this.props.mightFail(
        editAlert(this.state.editing.id, changedAlert),
        () => {
          this.closeEditAlertModal();
          this.loadAlerts();
        },
        showError
      );
      this.setState({loading: false});
    };
    closeEditAlertModal = () => this.setState({editing: null});

    openCopyAlertModal = (copying) => this.setState({copying});
    closeCopyAlertModal = () => this.setState({copying: null});

    render() {
      const {deleting, editing, copying, alerts, reports, webhooks, loading} = this.state;
      const {readOnly} = this.props;

      const isLoading = alerts === null || reports === null;

      return (
        <div className="AlertList">
          <EntityList
            name={t('alert.label-plural')}
            action={(bulkActive) =>
              !readOnly && (
                <Button main primary={!bulkActive} onClick={this.openAddAlertModal}>
                  {t('alert.createNew')}
                </Button>
              )
            }
            empty={
              <>
                {t('alert.notCreated')}
                {readOnly && (
                  <>
                    <br />
                    {t('alert.contactManager')}
                  </>
                )}
              </>
            }
            isLoading={isLoading}
            columns={[
              t('common.name'),
              t('report.label'),
              t('common.condition'),
              t('alert.recipient'),
            ]}
            bulkActions={!readOnly && [<BulkDeleter type="delete" deleteEntities={removeAlerts} />]}
            onChange={this.loadAlerts}
            data={
              !isLoading &&
              this.state.alerts.map((alert) => {
                const {id, name, webhook, emails, reportId, threshold, thresholdOperator} = alert;
                const inactive = webhook && emails?.length === 0 && !webhooks?.includes(webhook);

                return {
                  id,
                  entityType: 'alert',
                  icon: 'alert',
                  type: t('alert.label'),
                  name,
                  meta: [
                    reports.find((report) => report.id === reportId).name,
                    this.formatDescription(reportId, thresholdOperator, threshold),
                    emails?.join(', ') || webhook,
                  ],
                  warning: inactive && t('alert.inactiveStatus'),
                  actions: !readOnly && [
                    {
                      icon: 'edit',
                      text: t('common.edit'),
                      action: () => this.openEditAlertModal(alert),
                    },
                    {
                      icon: 'copy-document',
                      text: t('common.copy'),
                      action: () => this.openCopyAlertModal(alert),
                    },
                    {
                      icon: 'delete',
                      text: t('common.delete'),
                      action: () => this.setState({deleting: alert}),
                    },
                  ],
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
              onConfirm={(alert) => {
                if (editing.id) {
                  this.editAlert(alert);
                } else {
                  this.addAlert(alert);
                }
              }}
              disabled={loading}
            />
          )}
          {copying && (
            <CopyAlertModal
              initialAlertName={copying.name}
              onClose={this.closeCopyAlertModal}
              onConfirm={async (name) => {
                this.addAlert({...copying, name});
                this.closeCopyAlertModal();
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
        thresholdValue,
      });
    };
  }
);
