/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import update from 'immutability-helper';
import deepEqual from 'fast-deep-equal';
import {Redirect, withRouter} from 'react-router-dom';

import {withErrorHandling} from 'HOC';
import {nowDirty, nowPristine} from 'saveGuard';
import {
  ReportRenderer,
  LoadingIndicator,
  EntityNameForm,
  InstanceCount,
  Switch,
  Button,
} from 'components';
import {updateEntity, createEntity, evaluateReport, getCollection} from 'services';
import {showError} from 'notifications';
import {t} from 'translation';
import {withDocs} from 'HOC';

import ReportControlPanel from './controlPanels/ReportControlPanel';
import DecisionControlPanel from './controlPanels/DecisionControlPanel';
import CombinedReportPanel from './controlPanels/CombinedReportPanel';
import ConflictModal from './ConflictModal';
import {Configuration} from './controlPanels/Configuration';
import ReportWarnings from './ReportWarnings';
import Visualization from './Visualization';

export class ReportEdit extends React.Component {
  state = {
    loadingReportData: false,
    redirect: '',
    conflict: null,
    originalData: this.props.report,
    updatePromise: null,
    optimizeVersion: 'latest',
    report: this.props.report,
    serverError: this.props.error,
    shouldAutoReloadPreview: false,
    frozenReport: this.props.report,
  };

  componentDidMount() {
    const {report} = this.state;

    if (this.isReportComplete(report) && !report.result) {
      this.loadUpdatedReport(report);
      nowDirty(t('report.label'), this.save);
    }
  }

  showSaveError = (error) => {
    this.setState({
      conflict: null,
    });
    showError(error);
  };

  saveUpdatedReport = ({endpoint, id, name, data}) => {
    return new Promise((resolve, reject) => {
      this.props.mightFail(
        updateEntity(endpoint, id, {name, data}, {query: {force: this.state.conflict !== null}}),
        () => resolve(id),
        (error) => {
          if (error.status === 409 && error.conflictedItems) {
            this.setState({
              report: update(this.state.report, {name: {$set: name}}),
              conflict: error.conflictedItems.reduce(
                (obj, conflict) => {
                  obj[conflict.type].push(conflict);
                  return obj;
                },
                {alert: [], combined_report: []}
              ),
              updatePromise: resolve,
            });
          } else {
            reject(this.showSaveError(error));
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

        this.props.mightFail(createEntity(endpoint, {collectionId, name, data}), resolve, (error) =>
          reject(this.showSaveError(error))
        );
      } else {
        resolve(await this.saveUpdatedReport({endpoint, id, name, data}));
      }
    });
  };

  saveAndGoBack = async () => {
    const id = await this.save();
    if (this.state.updatePromise) {
      this.state.updatePromise(id);
      this.setState({updatePromise: null});
    }

    if (this.isReportDirty() || !this.state.shouldAutoReloadPreview) {
      await this.reEvaluateReport(this.state.report.data);
    }

    if (id) {
      nowPristine();
      this.props.updateOverview(
        update(this.state.report, {id: {$set: id}}),
        this.state.serverError
      );

      const params = new URLSearchParams(this.props.location.search);
      const returnTo = params.get('returnTo');

      let redirect = './';
      if (returnTo) {
        redirect = returnTo;
      } else if (this.props.isNew) {
        redirect = `../${id}/`;
      }

      this.setState({redirect});
    }
  };

  cancel = (evt) => {
    nowPristine();

    const params = new URLSearchParams(this.props.location.search);
    const returnTo = params.get('returnTo');

    if (returnTo) {
      evt.preventDefault();
      this.setState({redirect: returnTo});
    }

    this.setState({
      report: this.state.originalData,
    });
  };

  updateReport = async (change, needsReevaluation) => {
    const newReport = update(this.state.report.data, change);

    this.setState(
      ({report}) => ({
        report: update(report, {data: change}),
      }),
      this.dirtyCheck
    );

    if (needsReevaluation && this.state.shouldAutoReloadPreview) {
      this.reEvaluateReport(newReport);
    }
  };

  reEvaluateReport = async (newReport) => {
    const query = {
      ...this.state.report,
      data: newReport,
    };
    delete query.result;
    await this.loadUpdatedReport(query);
  };

  updateName = ({target: {value}}) => {
    this.setState(
      ({report}) => ({
        report: update(report, {name: {$set: value}}),
      }),
      this.dirtyCheck
    );
  };

  dirtyCheck = () => {
    if (this.isReportDirty()) {
      nowDirty(t('report.label'), this.save);
    } else {
      nowPristine();
    }
  };

  isReportDirty = () => !deepEqual(this.state.report, this.state.originalData);

  isReportComplete = ({data: {view, groupBy, visualization}, combined}) =>
    (view && groupBy && visualization) || combined;

  loadUpdatedReport = async (query) => {
    this.setState({report: query});

    if (this.isReportComplete(query)) {
      this.setState({loadingReportData: true});
      await this.loadReport({}, query);
      this.setState({loadingReportData: false});
    }
  };

  closeConflictModal = () => {
    this.state.updatePromise(null);
    this.setState({conflict: null, updatePromise: null});
  };

  setLoading = (value) => this.setState({loadingReportData: value});

  loadReport = (params, query = this.state.report) =>
    new Promise((resolve) =>
      this.props.mightFail(
        evaluateReport(query, [], params),
        (response) =>
          this.setState(
            {
              report: response,
              frozenReport: response,
              serverError: null,
            },
            resolve
          ),
        (serverError) => {
          if (serverError.reportDefinition) {
            this.setState(
              {
                report: serverError.reportDefinition,
                serverError,
              },
              resolve
            );
          } else {
            this.setState({serverError}, resolve);
          }
        }
      )
    );

  toggleAutoPreviewUpdate = async ({target: {checked: shouldReload}}) => {
    if (this.isReportDirty() && shouldReload) {
      await this.reEvaluateReport(this.state.report.data);
    }
    this.setState({shouldAutoReloadPreview: shouldReload});
  };

  render() {
    const {
      report,
      serverError,
      loadingReportData,
      conflict,
      redirect,
      shouldAutoReloadPreview,
      frozenReport,
    } = this.state;
    const {name, data, combined, reportType} = report;

    if (redirect) {
      return <Redirect to={redirect} />;
    }

    return (
      <div className="ReportEdit Report">
        <div className="Report__header">
          <div className="headerTopLine">
            <EntityNameForm
              name={name}
              entity="Report"
              isNew={this.props.isNew}
              onChange={this.updateName}
              onSave={this.saveAndGoBack}
              onCancel={this.cancel}
            />
            {!shouldAutoReloadPreview && (
              <Button
                main
                primary
                className="RunPreviewButton"
                disabled={this.state.loadingReportData || !this.isReportComplete(report)}
                onClick={() => this.reEvaluateReport(report.data)}
              >
                {t('report.updateReportPreview.buttonLabel')}
              </Button>
            )}
          </div>
          <div className="headerBottomLine">
            <InstanceCount noInfo report={report} />
            <div className="updatePreview">
              <div className="switch">
                <Switch
                  checked={shouldAutoReloadPreview}
                  onChange={this.toggleAutoPreviewUpdate}
                  label={t('report.updateReportPreview.switchLabel')}
                  labelPosition="left"
                />
              </div>
            </div>
          </div>
        </div>
        <div className="Report__view">
          <div className="Report__content">
            {!combined && (
              <div className="visualization">
                <div className="select">
                  <span className="label">{t(`report.visualization.label`)}</span>
                  <Visualization
                    type={reportType}
                    report={data}
                    onChange={(change) => this.updateReport(change, true)}
                  />
                </div>
                <Configuration
                  type={data.visualization}
                  onChange={this.updateReport}
                  disabled={loadingReportData}
                  report={report}
                  autoPreviewDisabled={!shouldAutoReloadPreview}
                />
              </div>
            )}

            {!combined && this.isReportComplete(report) && <ReportWarnings report={report} />}

            {loadingReportData ? (
              <LoadingIndicator />
            ) : (
              <ReportRenderer
                error={serverError}
                report={shouldAutoReloadPreview ? report : frozenReport}
                updateReport={this.updateReport}
                loadReport={this.loadReport}
              />
            )}
          </div>
          {!combined && reportType === 'process' && (
            <ReportControlPanel
              report={report}
              updateReport={this.updateReport}
              setLoading={this.setLoading}
            />
          )}
          {!combined && reportType === 'decision' && (
            <DecisionControlPanel
              report={report}
              updateReport={this.updateReport}
              setLoading={this.setLoading}
            />
          )}
          {combined && (
            <CombinedReportPanel
              report={report}
              updateReport={this.updateReport}
              loading={loadingReportData}
            />
          )}
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

export default withRouter(withErrorHandling(withDocs(ReportEdit)));
