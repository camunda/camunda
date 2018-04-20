import React from 'react';
import moment from 'moment';
import update from 'immutability-helper';

import {Link, Redirect} from 'react-router-dom';
import {
  Button,
  Modal,
  Input,
  ShareEntity,
  ReportView,
  Popover,
  Icon,
  ErrorMessage
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
import ControlPanel from './ControlPanel';

import ColumnSelection from './ColumnSelection';
import ColumnRearrangement from './ColumnRearrangement';

import './Report.css';

export default class Report extends React.Component {
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
      deleteModalVisible: false
    };
  }

  getTheOnlyDefinition = async () => {
    const avaliableDefinitions = await loadProcessDefinitions();
    if (avaliableDefinitions.length === 1) {
      const theOnlyKey = avaliableDefinitions[0].key;
      const latestVersion = avaliableDefinitions[0].versions[0].version;
      return {theOnlyKey, latestVersion};
    }

    return {theOnlyKey: null, latestVersion: null};
  };

  initializeReport = async () => {
    const {theOnlyKey, latestVersion} = await this.getTheOnlyDefinition();

    const data = {
      processDefinitionKey: theOnlyKey || '',
      processDefinitionVersion: latestVersion || '',
      view: {operation: '', entity: '', property: ''},
      groupBy: {type: '', unit: null},
      visualization: '',
      filter: [],
      configuration: {}
    };

    await this.loadXmlToConfiguration(data);

    return data;
  };

  componentDidMount = async () => {
    const isNew = this.isNew;
    const {name, lastModifier, lastModified, data} = await loadSingleReport(this.id);

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
      data.configuration = {...data.configuration, targetValue: {}};
      data.filter = data.filter.filter(
        ({type}) => type !== 'executedFlowNodes' && type !== 'variable'
      );
      await this.loadXmlToConfiguration(data);
    }

    this.setState({data});

    let reportResult;
    if (this.allFieldsAreSelected(data)) {
      reportResult = await getReportData(data);
    }
    if (!reportResult) {
      reportResult = {data};
    }
    this.setState({reportResult});
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
    return (
      this.isNotEmpty(processDefinitionKey) &&
      (this.viewGroupbyAndVisualizationFieldsAreSelected(view, groupBy, visualization) ||
        this.rawDataCombinationIsSelected(view.operation, visualization))
    );
  };

  viewGroupbyAndVisualizationFieldsAreSelected = (view, groupBy, visualization) => {
    const operation = view.operation;
    const type = groupBy.type;
    return this.isNotEmpty(operation) && this.isNotEmpty(type) && this.isNotEmpty(visualization);
  };

  rawDataCombinationIsSelected = (operation, visualization) => {
    return operation === 'rawData' && this.isNotEmpty(visualization);
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

        <ControlPanel {...data} reportResult={reportResult} onChange={this.updateReport} />
        <div className="Report__view">
          <div className="Report__content">
            {this.shouldShowColumnInteractions() ? (
              <ColumnRearrangement
                report={reportResult}
                onChange={this.updateConfiguration('columnOrder')}
              >
                <ReportView report={reportResult} />
              </ColumnRearrangement>
            ) : (
              <ReportView report={reportResult} />
            )}
          </div>
          {this.shouldShowColumnInteractions() && (
            <ColumnSelection
              columns={reportResult.result[0]}
              excludedColumns={data.configuration.excludedColumns}
              onChange={this.updateConfiguration('excludedColumns')}
            />
          )}
        </div>
      </div>
    );
  };

  shouldShowColumnInteractions = () => {
    const {data, reportResult} = this.state;

    return (
      data &&
      data.view &&
      data.view.operation === 'rawData' &&
      reportResult &&
      reportResult.result &&
      reportResult.result[0]
    );
  };

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

    const {loaded, redirect} = this.state;

    if (!loaded) {
      return <div className="report-loading-indicator">loading...</div>;
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
