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
  getReportData,
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
        name: null,
        lastModified: null,
        lastModifier: null,
        loaded: false,
        loadingReportData: false,
        redirect: false,
        originalName: null,
        confirmModalVisible: false,
        serverError: null,
        combined: false,
        reportType: 'process',
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

    initializeReport = async combined => {
      if (combined)
        return {
          reportIds: null,
          configuration: {}
        };
      const {theOnlyKey, latestVersion} = await this.getTheOnlyDefinition();

      const data = {
        processDefinitionKey: theOnlyKey || '',
        processDefinitionVersion: latestVersion || '',
        view: null,
        groupBy: null,
        visualization: null,
        filter: [],
        configuration: {},
        parameters: {}
      };

      await this.loadXmlToConfiguration(data);

      return data;
    };

    componentDidMount = async () => {
      const isNew = this.isNew;
      await this.props.mightFail(
        loadSingleReport(this.id),
        async response => {
          const {name, lastModifier, lastModified, data, combined, reportType} = response;
          const reportResult = await getReportData(this.id);
          const stateData = data || (await this.initializeReport(combined));
          const sharingEnabled = await isSharingEnabled();
          this.setState(
            {
              name,
              lastModifier,
              lastModified,
              loaded: true,
              data: stateData,
              originalData: {...stateData},
              reportResult: reportResult || {combined, reportType, data: stateData},
              reportType,
              originalName: name,
              combined,
              sharingEnabled
            },
            async () => {
              if (isNew) {
                this.save();
              }
            }
          );
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
        name: evt.target.value
      });
    };

    onlyVisualizationChanged(updates) {
      const {visualization, groupBy, view} = this.state.data;
      return (
        // there should be a visualization change
        updates.visualization &&
        // visualization data should be loaded before
        visualization &&
        // new visualization is different
        updates.visualization !== visualization &&
        // should be the same view
        (!updates.view || updates.view === view) &&
        // should be the same groupBy
        (!updates.groupBy || !groupBy || updates.groupBy.type === groupBy.type)
      );
    }

    loadXmlToConfiguration = async data => {
      if (data.processDefinitionKey && data.processDefinitionVersion) {
        const xml = await loadProcessDefinitionXml(
          data.processDefinitionKey,
          data.processDefinitionVersion
        );
        data.configuration = {...data.configuration, xml};
      }
    };

    allFieldsAreSelected = data => {
      const {processDefinitionKey, decisionDefinitionKey, view, groupBy, visualization} = data;
      const key = processDefinitionKey || decisionDefinitionKey;
      return this.isNotEmpty(key) && view && groupBy && visualization;
    };

    isNotEmpty = str => {
      return str && str.length > 0;
    };

    save = async evt => {
      await this.props.mightFail(
        saveReport(
          this.id,
          {
            name: this.state.name,
            data: this.state.data,
            reportType: this.state.reportType,
            combined: this.state.combined
          },
          this.state.conflict !== null
        ),
        () => {
          this.setState({
            confirmModalVisible: false,
            originalData: {...this.state.data},
            originalName: this.state.name,
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
      let reportResult = await getReportData(this.id);
      const {combined, reportType, originalData, originalName} = this.state;
      if (!reportResult) {
        reportResult = {combined, reportType, data: originalData};
      }
      this.setState({
        name: originalName,
        data: {...originalData},
        reportResult
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
      const {reportType, reportResult: {data, result}} = this.state;

      return (
        data.visualization === 'table' &&
        reportType === 'process' &&
        result &&
        Object.keys(result).length > 0
      );
    };

    maxRawDataEntriesExceeded = () => {
      if (!this.state.reportResult) return false;

      const {data, result, processInstanceCount} = this.state.reportResult;
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
      const {excludedColumns} = this.state.data.configuration;

      const queryString = excludedColumns
        ? `?excludedColumns=${excludedColumns
            .map(column => column.replace('var__', 'variable:'))
            .join(',')}`
        : '';

      return `api/export/csv/${this.id}/${encodeURIComponent(
        this.state.name.replace(/\s/g, '_')
      )}.csv${queryString}`;
    };

    updateReport = async (change, needsReevaluation) => {
      const newReport = update(this.state.data, change);

      if (needsReevaluation) {
        const {combined, reportType} = this.state;

        const query = {
          combined,
          reportType,
          data: newReport
        };

        this.setState({data: newReport, loadingReportData: true});
        this.setState({
          reportResult: (await getReportData(query)) || query,
          loadingReportData: false
        });
      } else {
        this.setState(({reportResult}) => ({
          data: newReport,
          reportResult: update(reportResult, {data: change})
        }));
      }
    };

    renderEditMode = () => {
      const {
        name,
        lastModifier,
        lastModified,
        data,
        reportResult,
        loadingReportData,
        combined,
        reportType,
        redirectToReport
      } = this.state;

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
                isInvalid={!this.state.name}
              />
              {!this.state.name && (
                <ErrorMessage className="Report__warning">
                  Report's name can not be empty
                </ErrorMessage>
              )}
              <div className="Report__metadata">
                Last modified {moment(lastModified).format('lll')} by {lastModifier}
              </div>
            </div>
            <div className="Report__tools">
              <button
                className="Button Report__tool-button Report__save-button"
                disabled={!this.state.name}
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
              <ReportControlPanel
                {...data}
                reportResult={reportResult}
                updateReport={this.updateReport}
              />
            )}

          {!combined &&
            reportType === 'decision' && (
              <DecisionControlPanel
                {...data}
                reportResult={reportResult}
                updateReport={this.updateReport}
              />
            )}

          {this.maxRawDataEntriesExceeded() && (
            <Message type="warning">
              The raw data table below only shows {reportResult.result.length} process instances out
              of a total of {reportResult.processInstanceCount}
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
                  report={reportResult}
                  applyAddons={this.applyAddons(ColumnRearrangement)}
                  customProps={{
                    table: {
                      updateSorting: this.updateSorting
                    }
                  }}
                />
              )}
            </div>
            {combined && (
              <CombinedReportPanel
                reportResult={reportResult}
                configuration={data.configuration}
                updateReport={this.updateReport}
              />
            )}
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
            report={this.state.reportResult}
            data={this.state.data}
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
      const {name, lastModifier, lastModified, reportResult, sharingEnabled} = this.state;
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
              <ReportView report={reportResult} />
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

      const {loaded, redirect, serverError, confirmModalVisible, conflict, name} = this.state;

      if (serverError) {
        return <ErrorPage entity="report" statusCode={serverError} />;
      }

      if (!loaded) {
        return <LoadingIndicator />;
      }

      if (redirect) {
        return <Redirect to="/reports" />;
      }

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
