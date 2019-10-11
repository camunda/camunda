/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component} from 'react';
import update from 'immutability-helper';
import {withErrorHandling} from 'HOC';
import moment from 'moment';
import {Redirect, withRouter} from 'react-router-dom';
import {
  ReportRenderer,
  LoadingIndicator,
  Message,
  ConfirmationModal,
  EntityNameForm
} from 'components';

import {
  incompatibleFilters,
  updateEntity,
  createEntity,
  evaluateReport,
  getCollection
} from 'services';
import {addNotification} from 'notifications';
import ReportControlPanel from './controlPanels/ReportControlPanel';
import DecisionControlPanel from './controlPanels/DecisionControlPanel';
import CombinedReportPanel from './controlPanels/CombinedReportPanel';
import {t} from 'translation';

export default withRouter(
  withErrorHandling(
    class ReportEdit extends Component {
      state = {
        loadingReportData: false,
        redirect: '',
        confirmModalVisible: false,
        conflict: null,
        originalData: this.props.report,
        report: this.props.report,
        saveLoading: false
      };

      showSaveError = name => {
        this.setState({
          saveLoading: false,
          confirmModalVisible: false,
          conflict: null
        });
        addNotification({text: t('report.cannotSave', {name}), type: 'error'});
      };

      saveUpdatedReport = ({endpoint, id, name, data}) => {
        this.props.mightFail(
          updateEntity(endpoint, id, {name, data}, {query: {force: this.state.conflict !== null}}),
          () => this.updateReportState(id, name),
          async error => {
            if (error.statusText === 'Conflict') {
              const conflictData = await error.json();
              this.setState({
                report: update(this.state.report, {name: {$set: name}}),
                confirmModalVisible: true,
                saveLoading: false,
                conflict: {
                  type: 'save',
                  items: conflictData.conflictedItems
                }
              });
            } else {
              this.showSaveError(name);
            }
          }
        );
      };

      updateReportState = (id, name) => {
        this.props.updateOverview(update(this.state.report, {name: {$set: name}, id: {$set: id}}));
        this.setState({redirect: this.props.isNew ? `../${id}/` : './'});
      };

      save = (evt, updatedName) => {
        this.setState({saveLoading: true});
        const {id, data, reportType, combined} = this.state.report;
        const name = updatedName || this.state.report.name;
        const endpoint = `report/${reportType}/${combined ? 'combined' : 'single'}`;

        if (this.props.isNew) {
          const collectionId = getCollection(this.props.location.pathname);

          this.props.mightFail(
            createEntity(endpoint, {collectionId, name, data}),
            id => this.updateReportState(id, name),
            () => this.showSaveError(name)
          );
        } else {
          this.saveUpdatedReport({endpoint, id, name, data});
        }
      };

      cancel = () => {
        this.setState({
          report: this.state.originalData
        });
      };

      updateReport = async (change, needsReevaluation) => {
        const newReport = update(this.state.report.data, change);

        this.setState(({report}) => ({
          report: update(report, {data: change})
        }));

        if (needsReevaluation) {
          const query = {
            ...this.state.report,
            data: newReport
          };
          await this.loadReport(query);
        }
      };

      loadReport = async query => {
        this.setState({
          loadingReportData: true
        });
        await this.props.mightFail(
          evaluateReport(query),
          response => {
            this.setState({
              report: response,
              loadingReportData: false
            });
          },
          async e => {
            const report = (await e.json()).reportDefinition;
            if (report) {
              this.setState({report, loadingReportData: false});
            }
            return;
          }
        );
      };

      showIncompleteResultWarning = () => {
        const {report} = this.state;
        if (
          !report ||
          !report.result ||
          typeof report.result.isComplete === 'undefined' ||
          !report.data.visualization
        ) {
          return false;
        }

        return !report.result.isComplete;
      };

      closeConfirmModal = () => {
        this.setState({
          confirmModalVisible: false,
          conflict: null
        });
      };

      render() {
        const {
          report,
          loadingReportData,
          confirmModalVisible,
          conflict,
          redirect,
          saveLoading
        } = this.state;
        const {name, lastModifier, lastModified, data, combined, reportType} = report;

        if (redirect) {
          return <Redirect to={redirect} />;
        }

        return (
          <>
            <ConfirmationModal
              open={confirmModalVisible}
              onClose={this.closeConfirmModal}
              onConfirm={this.save}
              conflict={conflict}
              entityName={name}
              loading={saveLoading}
            />
            <div className="Report">
              <div className="Report__header">
                <EntityNameForm
                  initialName={name}
                  entity="Report"
                  isNew={this.props.isNew}
                  onSave={this.save}
                  onCancel={this.cancel}
                  disabledButtons={saveLoading}
                />
                <div className="subHead">
                  <div className="metadata">
                    {t('common.entity.modified')} {moment(lastModified).format('lll')}{' '}
                    {t('common.entity.by')} {lastModifier}
                  </div>
                </div>
              </div>

              {!combined && reportType === 'process' && (
                <ReportControlPanel report={report} updateReport={this.updateReport} />
              )}

              {!combined && reportType === 'decision' && (
                <DecisionControlPanel report={report} updateReport={this.updateReport} />
              )}

              {this.showIncompleteResultWarning() && (
                <Message type="warning">
                  {t('report.incomplete', {
                    count: report.result.data.length || Object.keys(report.result.data).length
                  })}
                </Message>
              )}

              {data && data.filter && incompatibleFilters(data.filter) && (
                <Message type="warning">{t('common.filter.incompatibleFilters')}</Message>
              )}

              <div className="Report__view">
                <div className="Report__content">
                  {loadingReportData ? (
                    <LoadingIndicator />
                  ) : (
                    <ReportRenderer report={report} updateReport={this.updateReport} />
                  )}
                </div>
                {combined && (
                  <CombinedReportPanel report={report} updateReport={this.updateReport} />
                )}
              </div>
            </div>
          </>
        );
      }
    }
  )
);
