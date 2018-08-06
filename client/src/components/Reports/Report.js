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
  LoadingIndicator
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
        redirect: false,
        originalName: null,
        deleteModalVisible: false,
        serverError: null
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

    initializeReport = async () => {
      const {theOnlyKey, latestVersion} = await this.getTheOnlyDefinition();

      const data = {
        processDefinitionKey: theOnlyKey || '',
        processDefinitionVersion: latestVersion || '',
        view: null,
        groupBy: null,
        visualization: null,
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
          const {name, lastModifier, lastModified, data} = response;

          const reportResult = await getReportData(this.id);
          const stateData = data || (await this.initializeReport());
          this.setState(
            {
              name,
              lastModifier,
              lastModified,
              loaded: true,
              data: stateData,
              originalData: {...stateData},
              reportResult: reportResult || {data: stateData},
              originalName: name
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
        data.filter = data.filter.filter(
          ({type}) => type !== 'executedFlowNodes' && type !== 'variable'
        );
        await this.loadXmlToConfiguration(data);
      }

      if (updates.view) {
        data.configuration = {...data.configuration, targetValue: {}};
      }

      if (updates.visualization && updates.visualization !== this.state.data.visualization) {
        data.configuration = {...data.configuration, alwaysShowTooltips: false};
      }

      this.setState({data});

      const updatedSomethingOtherThanConfiguration = Object.keys(updates).find(
        entry => entry !== 'configuration'
      );
      if (updatedSomethingOtherThanConfiguration) {
        let reportResult;
        if (this.allFieldsAreSelected(data)) {
          reportResult = await getReportData(data);
        }
        if (!reportResult) {
          reportResult = {data};
        }
        this.setState({reportResult});
      } else {
        let reportResult = this.state.reportResult || {data};
        this.setState({
          reportResult: {
            ...reportResult,
            data: {...reportResult.data, configuration: data.configuration}
          }
        });
      }
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
        data: this.state.data
      });

      this.setState({
        originalData: {...this.state.data},
        originalName: this.state.name
      });
    };

    cancel = async () => {
      let reportResult = await getReportData(this.id);
      if (!reportResult) {
        reportResult = {data: this.state.originalData};
      }
      this.setState({
        name: this.state.originalName,
        data: {...this.state.originalData},
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

    constructCSVDownloadLink = () =>
      `/api/export/csv/${this.id}/${encodeURIComponent(this.state.name.replace(/\s/g, '_'))}.csv`;

    renderEditMode = () => {
      const {name, lastModifier, lastModified, data, reportResult} = this.state;
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

          <ReportControlPanel
            {...data}
            reportResult={reportResult}
            updateReport={this.updateReport}
          />
          <div className="Report__view">
            <ReportView
              report={reportResult}
              applyAddons={this.applyAddons(ColumnRearrangement, ColumnSelection)}
            />
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
      const changes = {data: {configuration: {[prop]: {$set: newValue}}}};
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
            onEnterPress={this.props.deleteReport}
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
