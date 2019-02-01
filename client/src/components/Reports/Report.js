import React from 'react';
import moment from 'moment';
import update from 'immutability-helper';
import {withErrorHandling} from 'HOC';

import {Link, Redirect} from 'react-router-dom';
import {
  Button,
  Input,
  ShareEntity,
  ReportView,
  Popover,
  Icon,
  ErrorMessage,
  ErrorPage,
  LoadingIndicator,
  Message,
  ConfirmationModal
} from 'components';

import {
  loadSingleReport,
  loadProcessDefinitionXml,
  remove,
  evaluateReport,
  saveReport,
  shareReport,
  revokeReportSharing,
  getSharedReport,
  isSharingEnabled
} from './service';

import {loadProcessDefinitions, checkDeleteConflict, incompatibleFilters} from 'services';
import ReportControlPanel from './ReportControlPanel';
import DecisionControlPanel from './DecisionControlPanel';
import CombinedReportPanel from './CombinedReportPanel';

import ColumnRearrangement from './ColumnRearrangement';

import './Report.scss';

export default withErrorHandling(
  class Report extends React.Component {
    constructor(props) {
      super(props);

      this.id = props.match.params.id;
      this.isNew = props.location.search === '?new';

      this.state = {
        loaded: false,
        loadingReportData: false,
        redirect: false,
        confirmModalVisible: false,
        serverError: null,
        redirectToReport: false,
        conflict: null,
        sharingEnabled: false
      };
    }

    getTheOnlyDefinition = async () => {
      const availableDefinitions = await loadProcessDefinitions();
      if (availableDefinitions.length === 1) {
        const theOnlyKey = availableDefinitions[0].key;
        const latestVersion = availableDefinitions[0].versions[0].version;
        return {theOnlyKey, latestVersion};
      }

      return {theOnlyKey: null, latestVersion: null};
    };

    loadTheOnlyDefinition = async () => {
      const {theOnlyKey, latestVersion} = await this.getTheOnlyDefinition();

      const data = {
        processDefinitionKey: theOnlyKey || '',
        processDefinitionVersion: latestVersion || ''
      };

      await this.loadXmlToConfiguration(data);

      return data;
    };

    componentDidMount = async () => {
      await this.props.mightFail(
        loadSingleReport(this.id),
        async response => {
          this.setState({
            loaded: true,
            originalData: response,
            report: (await evaluateReport(this.id)) || response,
            sharingEnabled: await isSharingEnabled()
          });
        },
        error => {
          const serverError = error.status;
          this.setState({
            serverError
          });
          return;
        }
      );
    };

    deleteReport = async evt => {
      await remove(this.id);

      this.setState({
        redirect: true
      });
    };

    updateName = evt => {
      this.setState({
        report: update(this.state.report, {name: {$set: evt.target.value}})
      });
    };

    loadXmlToConfiguration = async data => {
      if (data.processDefinitionKey && data.processDefinitionVersion) {
        const xml = await loadProcessDefinitionXml(
          data.processDefinitionKey,
          data.processDefinitionVersion
        );
        data.configuration = {...data.configuration, xml};
      }
    };

    isNotEmpty = str => {
      return str && str.length > 0;
    };

    save = async evt => {
      const {name, data, reportType, combined} = this.state.report;
      await this.props.mightFail(
        saveReport(this.id, {name, data, reportType, combined}, this.state.conflict !== null),
        () => {
          this.setState({
            confirmModalVisible: false,
            originalData: this.state.report,
            redirectToReport: !!evt,
            conflict: null
          });
        },
        async error => {
          if (error.statusText === 'Conflict') {
            const conflictData = await error.json();
            this.setState({
              confirmModalVisible: true,
              conflict: {
                type: 'Save',
                items: conflictData.conflictedItems
              }
            });
          }
        }
      );
    };

    cancel = async () => {
      let report = await evaluateReport(this.id);
      const {originalData} = this.state;
      if (!report) {
        report = originalData;
      }
      this.setState({
        report
      });
    };

    showDeleteModal = async () => {
      let conflictState = {};
      const response = await checkDeleteConflict(this.id, 'report');
      if (response && response.conflictedItems && response.conflictedItems.length) {
        conflictState = {
          conflict: {
            type: 'Delete',
            items: response.conflictedItems
          }
        };
      }

      this.setState({
        confirmModalVisible: true,
        ...conflictState
      });
    };

    closeConfirmModal = () => {
      this.setState({
        confirmModalVisible: false,
        conflict: null
      });
    };

    shouldShowCSVDownload = () => {
      const {report: {result}} = this.state;
      return result;
    };

    maxRawDataEntriesExceeded = () => {
      if (!this.state.report) return false;

      const {data, result, processInstanceCount} = this.state.report;
      return !!(
        result &&
        result.length &&
        data &&
        data.visualization === 'table' &&
        data.view &&
        data.view.operation === 'rawData' &&
        processInstanceCount > result.length
      );
    };

    constructCSVDownloadLink = () => {
      const {excludedColumns} = this.state.report.data.configuration;

      const queryString = excludedColumns
        ? `?excludedColumns=${excludedColumns
            .map(column => column.replace('var__', 'variable:'))
            .join(',')}`
        : '';

      return `api/export/csv/${this.id}/${encodeURIComponent(
        this.state.report.name.replace(/\s/g, '_')
      )}.csv${queryString}`;
    };

    updateReport = async (change, needsReevaluation) => {
      const newReport = update(this.state.report.data, change);

      if (needsReevaluation) {
        const query = {
          ...this.state.report,
          data: newReport
        };

        this.setState({
          loadingReportData: true
        });
        this.setState({
          report: (await evaluateReport(query)) || query,
          loadingReportData: false
        });
      } else {
        this.setState(({report}) => ({
          report: update(report, {data: change})
        }));
      }
    };

    renderEditMode = () => {
      const {report, loadingReportData, redirectToReport} = this.state;
      const {name, lastModifier, lastModified, data, combined, reportType} = report;

      return (
        <div className="Report">
          <div className="Report__header">
            <div className="Report__name-container">
              <Input
                id="name"
                type="text"
                ref={this.inputRef}
                onChange={this.updateName}
                value={name || ''}
                className="Report__name-input"
                placeholder="Report Name"
                isInvalid={!name}
              />
              {!name && (
                <ErrorMessage className="Report__warning">
                  {"Report's name can not be empty"}
                </ErrorMessage>
              )}
              <div className="Report__metadata">
                Last modified {moment(lastModified).format('lll')} by {lastModifier}
              </div>
            </div>
            <div className="Report__tools">
              <button
                className="Button Report__tool-button Report__save-button"
                disabled={!name}
                onClick={this.save}
              >
                <Icon type="check" />
                Save
              </button>
              {redirectToReport && <Redirect to={`/report/${this.id}`} />}
              <Link
                className="Button Report__tool-button Report__cancel-button"
                to={`/report/${this.id}`}
                onClick={this.cancel}
              >
                <Icon type="stop" />
                Cancel
              </Link>
            </div>
          </div>

          {!combined &&
            reportType === 'process' && (
              <ReportControlPanel report={report} updateReport={this.updateReport} />
            )}

          {!combined &&
            reportType === 'decision' && (
              <DecisionControlPanel report={report} updateReport={this.updateReport} />
            )}

          {this.maxRawDataEntriesExceeded() && (
            <Message type="warning">
              The raw data table below only shows {report.result.length} process instances out of a
              total of {report.processInstanceCount}
            </Message>
          )}

          {data &&
            data.filter &&
            incompatibleFilters(data.filter) && (
              <Message type="warning">
                No data is shown since the combination of filters is incompatible with each other
              </Message>
            )}

          <div className="Report__view">
            <div className="Report__content">
              {loadingReportData ? (
                <LoadingIndicator />
              ) : (
                <ReportView
                  report={report}
                  applyAddons={this.applyAddons(ColumnRearrangement)}
                  customProps={{
                    table: {
                      updateSorting: this.updateSorting
                    }
                  }}
                />
              )}
            </div>
            {combined && <CombinedReportPanel report={report} updateReport={this.updateReport} />}
          </div>
        </div>
      );
    };

    updateSorting = (by, order) => {
      this.updateReport({parameters: {sorting: {$set: {by, order}}}}, true);
    };

    applyAddons = (...addons) => (Component, props) =>
      this.renderWrapperAddons(addons, Component, props);

    renderWrapperAddons = ([{Wrapper}, ...rest], Component, props) => {
      const renderedRest = rest.length ? (
        this.renderWrapperAddons(rest, Component, props)
      ) : (
        <Component {...props} />
      );

      if (Wrapper) {
        return (
          <Wrapper
            report={this.state.report}
            data={this.state.report.data}
            updateReport={this.updateReport}
          >
            {renderedRest}
          </Wrapper>
        );
      } else {
        return renderedRest;
      }
    };

    renderViewMode = () => {
      const {report, sharingEnabled} = this.state;
      const {name, lastModifier, lastModified} = report;
      return (
        <div className="Report">
          <div className="Report__header">
            <div className="Report__name-container">
              <h1 className="Report__name">{name}</h1>
              <div className="Report__metadata">
                Last modified {moment(lastModified).format('lll')} by {lastModifier}
              </div>
            </div>
            <div className="Report__tools">
              <Link
                className="Report__tool-button Report__edit-button"
                to={`/report/${this.id}/edit`}
              >
                <Button>
                  <Icon type="edit" />
                  Edit
                </Button>
              </Link>
              <Button
                className="Report__tool-button Report__delete-button"
                onClick={this.showDeleteModal}
              >
                <Icon type="delete" />
                Delete
              </Button>
              <Popover
                className="Report__tool-button Report__share-button"
                icon="share"
                title="Share"
                tooltip={!sharingEnabled ? 'Sharing is disabled per configuration' : ''}
                disabled={!sharingEnabled}
              >
                <ShareEntity
                  type="report"
                  resourceId={this.id}
                  shareEntity={shareReport}
                  revokeEntitySharing={revokeReportSharing}
                  getSharedEntity={getSharedReport}
                />
              </Popover>
              {this.shouldShowCSVDownload() && (
                <a
                  className="Report__tool-button Report__csv-download-button"
                  href={this.constructCSVDownloadLink()}
                >
                  <Button>
                    <Icon type="save" />
                    Download CSV
                  </Button>
                </a>
              )}
            </div>
          </div>
          <div className="Report__view">
            <div className="Report__content">
              <ReportView report={report} />
            </div>
          </div>
        </div>
      );
    };

    inputRef = input => {
      this.nameInput = input;
    };

    componentDidUpdate() {
      if (this.state.redirectToReport) this.setState({redirectToReport: false});
      if (this.nameInput && this.isNew) {
        this.nameInput.focus();
        this.nameInput.select();
        this.isNew = false;
      }
    }

    render() {
      const {viewMode} = this.props.match.params;

      const {loaded, report, redirect, serverError, confirmModalVisible, conflict} = this.state;

      if (serverError) {
        return <ErrorPage entity="report" statusCode={serverError} />;
      }

      if (!loaded) {
        return <LoadingIndicator />;
      }

      if (redirect) {
        return <Redirect to="/reports" />;
      }

      const {name} = report;
      return (
        <div className="Report-container">
          <ConfirmationModal
            open={confirmModalVisible}
            onClose={this.closeConfirmModal}
            onConfirm={conflict && conflict.type === 'Save' ? this.save : this.deleteReport}
            conflict={conflict}
            entityName={name}
          />
          {viewMode === 'edit' ? this.renderEditMode() : this.renderViewMode()}
        </div>
      );
    }
  }
);
