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
import {Button, Loading, Toggle} from '@carbon/react';
import classnames from 'classnames';

import {withErrorHandling} from 'HOC';
import {nowDirty, nowPristine} from 'saveGuard';
import {ReportRenderer, EntityNameForm, InstanceCount, InstanceViewTable} from 'components';
import {updateEntity, createEntity, evaluateReport, getCollection} from 'services';
import {showError} from 'notifications';
import {t} from 'translation';
import {withDocs} from 'HOC';
import {track} from 'tracking';

import ReportControlPanel from './controlPanels/ReportControlPanel';
import DecisionControlPanel from './controlPanels/DecisionControlPanel';
import CombinedReportPanel from './controlPanels/CombinedReportPanel';
import ConflictModal from './ConflictModal';
import {Configuration} from './controlPanels/Configuration';
import ReportWarnings from './ReportWarnings';
import Visualization from './Visualization';
import {CollapsibleContainer} from './CollapsibleContainer';

import './ReportEdit.scss';

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
    shouldAutoReloadPreview: sessionStorage.getItem('shouldAutoReloadPreview') === 'true',
    frozenReport: this.props.report,
    runButtonLoading: false,
    showReportRenderer: true,
  };

  containerRef = React.createRef(null);

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

  saveUpdatedReport = ({endpoint, id, name, description, data}) => {
    return new Promise((resolve, reject) => {
      this.props.mightFail(
        updateEntity(
          endpoint,
          id,
          {name, description, data},
          {query: {force: this.state.conflict !== null}}
        ),
        () => resolve(id),
        (error) => {
          if (error.status === 409 && error.conflictedItems) {
            this.setState({
              report: update(this.state.report, {
                name: {$set: name},
                description: {$set: description},
              }),
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
      const {id, name, description, data, reportType, combined} = this.state.report;
      const endpoint = `report/${reportType}/${combined ? 'combined' : 'single'}`;

      if (this.props.isNew) {
        const collectionId = getCollection(this.props.location.pathname);

        this.props.mightFail(
          createEntity(endpoint, {collectionId, name, description, data}),
          resolve,
          (error) => reject(this.showSaveError(error))
        );
      } else {
        resolve(await this.saveUpdatedReport({endpoint, id, name, description, data}));
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

    if (isRearrangementChanged(change)) {
      this.setState(({frozenReport}) => ({
        frozenReport: update(frozenReport, {data: change}),
      }));
    }

    if (needsReevaluation && this.state.shouldAutoReloadPreview) {
      await this.reEvaluateReport(newReport);
    }
  };

  reEvaluateReport = async (newReport) => {
    const query = {
      ...this.state.report,
      data: newReport,
    };
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

  updateDescription = (description) => {
    track('editDescription', {entity: 'report', entityId: this.state.report.id});
    this.setState(
      ({report}) => ({
        report: update(report, {description: {$set: description}}),
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

  toggleAutoPreviewUpdate = async (shouldReload) => {
    if (this.isReportDirty() && shouldReload) {
      await this.reEvaluateReport(this.state.report.data);
    }
    this.setState({shouldAutoReloadPreview: shouldReload});
    sessionStorage.setItem('shouldAutoReloadPreview', shouldReload);
  };

  runReportPreviewUpdate = async () => {
    this.setState({runButtonLoading: true});
    await this.reEvaluateReport(this.state.report.data);
    this.setState({runButtonLoading: false});
  };

  showTable = (sectionState) => {
    if (sectionState !== 'maximized') {
      this.setState({showReportRenderer: true});
    }
  };

  handleTableExpand = (currentState, newState) => {
    track('changeRawDataView', {
      from: currentState,
      to: newState,
      reportType: this.state.report.data?.visualization,
    });
    this.setState({showReportRenderer: newState !== 'maximized'});
  };

  handleTableCollapse = (currentState, newState) => {
    track('changeRawDataView', {
      from: currentState,
      to: newState,
      reportType: this.state.report.data?.visualization,
    });
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
      runButtonLoading,
      showReportRenderer,
    } = this.state;
    const {name, description, data, combined, reportType} = report;

    if (redirect) {
      return <Redirect to={redirect} />;
    }

    return (
      <div className="ReportEdit Report">
        <div className="reportHeader">
          <div className="headerTopLine">
            <EntityNameForm
              name={name}
              entity="Report"
              isNew={this.props.isNew}
              onChange={this.updateName}
              onSave={this.saveAndGoBack}
              onCancel={this.cancel}
              description={description}
              onDescriptionChange={this.updateDescription}
            />
            {!shouldAutoReloadPreview && (
              <Button
                kind="primary"
                size="md"
                className="RunPreviewButton"
                disabled={this.state.loadingReportData || !this.isReportComplete(report)}
                onClick={this.runReportPreviewUpdate}
              >
                {t('report.updateReportPreview.buttonLabel')}
              </Button>
            )}
          </div>
          <div className="headerBottomLine">
            <InstanceCount noInfo report={report} />
            <Toggle
              id="updatePreview"
              className="updatePreview"
              size="sm"
              toggled={shouldAutoReloadPreview}
              onToggle={this.toggleAutoPreviewUpdate}
              labelA={t('report.updateReportPreview.switchLabel')}
              labelB={t('report.updateReportPreview.switchLabel')}
            />
          </div>
        </div>
        <div className="Report__view" ref={this.containerRef}>
          <div className="viewsContainer">
            <div className="mainView">
              <div className={classnames('Report__content', {hidden: !showReportRenderer})}>
                {!combined && (
                  <div className="visualization">
                    <Visualization
                      type={reportType}
                      report={data}
                      onChange={(change) => this.updateReport(change, true)}
                    />
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

                {(shouldAutoReloadPreview || runButtonLoading) && loadingReportData ? (
                  <Loading withOverlay={false} className="loading" />
                ) : (
                  showReportRenderer && (
                    <ReportRenderer
                      error={serverError}
                      report={shouldAutoReloadPreview ? report : frozenReport}
                      updateReport={this.updateReport}
                      loadReport={this.loadReport}
                    />
                  )
                )}
              </div>
            </div>
            {!combined &&
              typeof report.result !== 'undefined' &&
              report.data?.visualization !== 'table' && (
                <CollapsibleContainer
                  maxHeight={this.containerRef.current?.offsetHeight}
                  initialState="minimized"
                  onTransitionEnd={this.showTable}
                  onExpand={this.handleTableExpand}
                  onCollapse={this.handleTableCollapse}
                  title={t('report.view.rawData')}
                >
                  <InstanceViewTable report={shouldAutoReloadPreview ? report : frozenReport} />
                </CollapsibleContainer>
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

function isRearrangementChanged(change) {
  return !!change?.configuration?.tableColumns?.columnOrder;
}
