import React from 'react';
import moment from 'moment';
import update from 'immutability-helper';
import {withErrorHandling} from 'HOC';

import {Link, Redirect} from 'react-router-dom';
import {
  Button,
  Modal,
  Input,
  ShareEntity,
  ReportView,
  Popover,
  Icon,
  ErrorMessage,
  ErrorPage,
  LoadingIndicator,
  Message
} from 'components';

import {
  loadSingleReport,
  loadProcessDefinitionXml,
  remove,
  getReportData,
  saveReport,
  shareReport,
  revokeReportSharing,
  getSharedReport
} from './service';

import {loadProcessDefinitions} from 'services';
import ReportControlPanel from './ReportControlPanel';
import CombinedSelectionPanel from './CombinedSelectionPanel';

import ColumnSelection from './ColumnSelection';
import ColumnRearrangement from './ColumnRearrangement';

import './Report.css';

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
        deleteModalVisible: false,
        serverError: null,
        reportType: null
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

    initializeReport = async reportType => {
      if (reportType === 'combined')
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
        processPart: null,
        filter: [],
        configuration: {}
      };

      await this.loadXmlToConfiguration(data);

      return data;
    };

    componentDidMount = async () => {
      const isNew = this.isNew;
      await this.props.mightFail(
        loadSingleReport(this.id),
        async response => {
          const {name, lastModifier, lastModified, data, reportType} = response;

          const reportResult = await getReportData(this.id);
          const stateData = data || (await this.initializeReport(reportType));
          this.setState(
            {
              name,
              lastModifier,
              lastModified,
              loaded: true,
              data: stateData,
              originalData: {...stateData},
              reportResult: reportResult || {reportType, data: stateData},
              originalName: name,
              reportType
            },
            () => {
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

    updateReport = async updates => {
      const data = {
        ...this.state.data,
        ...updates
      };

      const processDefinitionWasUpdated =
        updates.processDefinitionKey || updates.processDefinitionVersion;
      if (processDefinitionWasUpdated) {
        data.configuration = {...data.configuration, excludedColumns: [], targetValue: {}};
        data.processPart = null;

        if (data.groupBy && data.groupBy.type === 'variable') {
          data.groupBy = null;
          data.visualization = null;
        }
        await this.loadXmlToConfiguration(data);
      }

      if (updates.view) {
        data.configuration = {...data.configuration, targetValue: {}};
        data.processPart = null;
      }

      if (updates.visualization && updates.visualization !== this.state.data.visualization) {
        data.configuration = {...data.configuration, alwaysShowTooltips: false};
      }

      this.setState({data});

      this.updateReportResult(updates, data);
    };

    updateReportResult = async (updates, data) => {
      const {reportType, reportResult} = this.state;

      const updatedSomethingOtherThanConfiguration = Object.keys(updates).find(
        entry => entry !== 'configuration'
      );
      if (updatedSomethingOtherThanConfiguration && !this.onlyVisualizationChanged(updates)) {
        let newReportResult;
        if (reportType === 'combined' || this.allFieldsAreSelected(data)) {
          this.setState({loadingReportData: true});
          newReportResult = await getReportData(data, reportType);
          this.setState({loadingReportData: false});
        }
        if (!newReportResult) {
          newReportResult = {reportType, data};
        }
        this.setState({reportResult: newReportResult});
      } else {
        let newReportResult = reportResult || {reportType, data};
        this.setState({
          reportResult: {
            ...newReportResult,
            data: {
              ...newReportResult.data,
              configuration: data.configuration,
              ...(data.visualization && {visualization: data.visualization}),
              ...(data.reportIds && {reportIds: data.reportIds})
            }
          }
        });
      }
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
      const {processDefinitionKey, view, groupBy, visualization} = data;
      return this.isNotEmpty(processDefinitionKey) && view && groupBy && visualization;
    };

    isNotEmpty = str => {
      return str !== null && str.length > 0;
    };

    save = async evt => {
      saveReport(this.id, {
        name: this.state.name,
        data: this.state.data,
        reportType: this.state.reportType
      });

      this.setState({
        originalData: {...this.state.data},
        originalName: this.state.name
      });
    };

    cancel = async () => {
      let reportResult = await getReportData(this.id);
      const {reportType, originalData, originalName} = this.state;
      if (!reportResult) {
        reportResult = {reportType, data: originalData};
      }
      this.setState({
        name: originalName,
        data: {...originalData},
        reportResult
      });
    };

    showDeleteModal = () => {
      this.setState({
        deleteModalVisible: true
      });
    };

    closeDeleteModal = () => {
      this.setState({
        deleteModalVisible: false
      });
    };

    shouldShowCSVDownload = () => {
      const {data, result} = this.state.reportResult;

      return data.visualization === 'table' && result && Object.keys(result).length > 0;
    };

    maxRawDataEntriesExceeded = () => {
      if (!this.state.reportResult) return false;

      const {data, result, processInstanceCount} = this.state.reportResult;
      return (
        result &&
        result.length &&
        data &&
        data.visualization === 'table' &&
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

    renderEditMode = () => {
      const {
        name,
        lastModifier,
        lastModified,
        data,
        reportResult,
        loadingReportData,
        reportType
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
              <Link
                className="Button Report__tool-button Report__save-button"
                to={`/report/${this.id}`}
                disabled={!this.state.name}
                onClick={this.save}
              >
                <Icon type="check" />
                Save
              </Link>
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

          {reportType === 'single' && (
            <ReportControlPanel
              {...data}
              reportResult={reportResult}
              updateReport={this.updateReport}
            />
          )}

          {this.maxRawDataEntriesExceeded() && (
            <Message type="warning">
              The raw data table below only shows the last 1000 process instances
            </Message>
          )}

          <div className="reportViewWrapper">
            <div className="Report__view">
              {loadingReportData ? (
                <LoadingIndicator />
              ) : (
                <ReportView
                  report={reportResult}
                  applyAddons={this.applyAddons(ColumnRearrangement, ColumnSelection)}
                />
              )}
            </div>
            {reportType === 'combined' && (
              <div className="combinedSelectionPanel">
                <CombinedSelectionPanel
                  reportResult={reportResult}
                  updateReport={this.updateReport}
                />
              </div>
            )}
          </div>
        </div>
      );
    };

    applyAddons = (...addons) => (Component, props) => (
      <React.Fragment>
        <div className="Report__content">{this.renderWrapperAddons(addons, Component, props)}</div>
        {this.renderContentAddons(addons)}
      </React.Fragment>
    );

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
            change={this.updateConfiguration}
          >
            {renderedRest}
          </Wrapper>
        );
      } else {
        return renderedRest;
      }
    };

    renderContentAddons = addons =>
      addons.map(({Content}, idx) => {
        if (Content) {
          return (
            <Content
              key={idx}
              report={this.state.reportResult}
              data={this.state.data}
              change={this.updateConfiguration}
            />
          );
        } else {
          return null;
        }
      });

    updateConfiguration = prop => newValue => {
      const changes = {
        reportType: {$set: this.state.reportType},
        data: {configuration: {[prop]: {$set: newValue}}}
      };
      this.setState(
        update(this.state, {
          ...changes,
          reportResult: changes
        })
      );
    };

    renderViewMode = () => {
      const {name, lastModifier, lastModified, reportResult, deleteModalVisible} = this.state;

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
          <Modal
            open={deleteModalVisible}
            onClose={this.closeDeleteModal}
            onConfirm={this.deleteReport}
            className="Report__delete-modal"
          >
            <Modal.Header>Delete {this.state.name}</Modal.Header>
            <Modal.Content>
              <p>You are about to delete {this.state.name}. Are you sure you want to proceed?</p>
            </Modal.Content>
            <Modal.Actions>
              <Button className="Report__close-delete-modal-button" onClick={this.closeDeleteModal}>
                Cancel
              </Button>
              <Button
                type="primary"
                color="red"
                className="Report__delete-report-modal-button"
                onClick={this.deleteReport}
              >
                Delete
              </Button>
            </Modal.Actions>
          </Modal>
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
      if (this.nameInput && this.isNew) {
        this.nameInput.focus();
        this.nameInput.select();
        this.isNew = false;
      }
    }

    render() {
      const {viewMode} = this.props.match.params;

      const {loaded, redirect, serverError} = this.state;

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
          {viewMode === 'edit' ? this.renderEditMode() : this.renderViewMode()}
        </div>
      );
    }
  }
);
