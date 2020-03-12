/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import update from 'immutability-helper';
import deepEqual from 'deep-equal';
import {Redirect, withRouter} from 'react-router-dom';
import moment from 'moment';

import {withErrorHandling} from 'HOC';
import {nowDirty, nowPristine} from 'saveGuard';
import {ReportRenderer, LoadingIndicator, MessageBox, EntityNameForm} from 'components';

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
import ConflictModal from './ConflictModal';

export default withRouter(
  withErrorHandling(
    class ReportEdit extends React.Component {
      state = {
        loadingReportData: false,
        redirect: '',
        conflict: null,
        originalData: this.props.report,
        report: this.props.report
      };

      showSaveError = name => {
        this.setState({
          conflict: null
        });
        addNotification({text: t('report.cannotSave', {name}), type: 'error'});
      };

      saveUpdatedReport = ({endpoint, id, name, data}) => {
        return new Promise((resolve, reject) => {
          this.props.mightFail(
            updateEntity(
              endpoint,
              id,
              {name, data},
              {query: {force: this.state.conflict !== null}}
            ),
            () => resolve(id),
            async error => {
              if (error.statusText === 'Conflict') {
                const {conflictedItems} = await error.json();
                this.setState({
                  report: update(this.state.report, {name: {$set: name}}),
                  conflict: conflictedItems.reduce(
                    (obj, conflict) => {
                      obj[conflict.type].push(conflict);
                      return obj;
                    },
                    {alert: [], combined_report: []}
                  )
                });
                resolve(null);
              } else {
                reject(this.showSaveError(name));
              }
            }
          );
        });
      };

      save = () => {
        return new Promise(async (resolve, reject) => {
          const {id, name, data, reportType, combined} = this.state.report;
          const endpoint = `report/${reportType}/${combined ? 'combined' : 'single'}`;

          if (this.props.isNew) {
            const collectionId = getCollection(this.props.location.pathname);

            this.props.mightFail(createEntity(endpoint, {collectionId, name, data}), resolve, () =>
              reject(this.showSaveError(name))
            );
          } else {
            resolve(await this.saveUpdatedReport({endpoint, id, name, data}));
          }
        });
      };

      saveAndGoBack = async () => {
        const id = await this.save();
        if (id) {
          nowPristine();
          this.props.updateOverview(update(this.state.report, {id: {$set: id}}));
          this.setState({redirect: this.props.isNew ? `../${id}/` : './'});
        }
      };

      cancel = () => {
        nowPristine();
        this.setState({
          report: this.state.originalData
        });
      };

      updateReport = async (change, needsReevaluation) => {
        const newReport = update(this.state.report.data, change);

        this.setState(
          ({report}) => ({
            report: update(report, {data: change})
          }),
          this.dirtyCheck
        );

        if (needsReevaluation) {
          const query = {
            ...this.state.report,
            data: newReport
          };
          await this.loadReport(query);
        }
      };

      updateName = ({target: {value}}) => {
        this.setState(
          ({report}) => ({
            report: update(report, {name: {$set: value}})
          }),
          this.dirtyCheck
        );
      };

      dirtyCheck = () => {
        if (deepEqual(this.state.report, this.state.originalData)) {
          nowPristine();
        } else {
          nowDirty(t('report.label'), this.save);
        }
      };

      loadReport = async query => {
        this.setState(({report}) => ({
          loadingReportData: true,
          // reset the result during the evaluation
          report: update(report, {$unset: ['result']})
        }));
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

      closeConflictModal = () => this.setState({conflict: null});

      render() {
        const {report, loadingReportData, conflict, redirect} = this.state;
        const {name, lastModifier, lastModified, data, combined, reportType} = report;

        if (redirect) {
          return <Redirect to={redirect} />;
        }

        return (
          <div className="Report">
            <div className="Report__header">
              <EntityNameForm
                name={name}
                entity="Report"
                isNew={this.props.isNew}
                onChange={this.updateName}
                onSave={this.saveAndGoBack}
                onCancel={this.cancel}
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
              <MessageBox type="warning">
                {t('report.incomplete', {
                  count: report.result.data.length || Object.keys(report.result.data).length
                })}
              </MessageBox>
            )}

            {data && data.filter && incompatibleFilters(data.filter) && (
              <MessageBox type="warning">{t('common.filter.incompatibleFilters')}</MessageBox>
            )}

            <div className="Report__view">
              <div className="Report__content">
                {loadingReportData ? (
                  <LoadingIndicator />
                ) : (
                  <ReportRenderer report={report} updateReport={this.updateReport} />
                )}
              </div>
              {combined && <CombinedReportPanel report={report} updateReport={this.updateReport} />}
            </div>
            <ConflictModal
              conflict={conflict}
              onClose={this.closeConflictModal}
              onConfirm={this.saveAndGoBack}
            />
          </div>
        );
      }
    }
  )
);
