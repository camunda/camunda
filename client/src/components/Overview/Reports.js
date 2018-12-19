import React from 'react';
import {
  Button,
  Message,
  LoadingIndicator,
  Input,
  ConfirmationModal,
  Icon,
  Dropdown
} from 'components';

import {withErrorHandling} from 'HOC';
import {Redirect, Link} from 'react-router-dom';
import {formatters, checkDeleteConflict} from 'services';

import entityIcons from './entityIcons';
import {createReport, loadReports, deleteReport, getReportInfo, getReportIcon} from './service';
import LastModified from './subComponents/LastModified';
import NoEntities from './subComponents/NoEntities';

import './Reports.scss';

const HeaderIcon = entityIcons.report.header.Component;

class Reports extends React.Component {
  state = {
    redirect: false,
    loading: true,
    entities: [],
    deleting: false,
    conflicts: [],
    search: ''
  };

  loadReports = async () => {
    this.props.mightFail(loadReports(), response => {
      this.setState({
        entities: response,
        loading: false
      });
    });
  };

  componentDidMount = this.loadReports;

  createCombinedReport = async () => this.setState({redirect: await createReport(true, 'process')});
  createReport = async () => this.setState({redirect: await createReport(false, 'process')});
  createDMNReport = async () => this.setState({redirect: await createReport(false, 'decision')});

  duplicateReport = report => async evt => {
    evt.target.blur();

    const copy = {
      ...report,
      name: report.name + ' - Copy'
    };
    await createReport(report.combined, report.reportType, copy);
    this.loadReports();
  };

  showDeleteModalFor = report => async () => {
    const {conflictedItems} = await checkDeleteConflict(report.id);

    this.setState({
      deleting: report,
      conflicts: conflictedItems
    });
  };
  hideDeleteModal = () => this.setState({deleting: false, conflicts: []});

  deleteReport = async () => {
    await deleteReport(this.state.deleting.id);

    this.setState({
      deleting: false,
      loading: true,
      conflicts: []
    });

    this.loadReports();
  };

  updateSearch = ({target: {value}}) => this.setState({search: value});

  render() {
    if (this.state.redirect) {
      return <Redirect to={`/report/${this.state.redirect}/edit?new`} />;
    }

    const error = this.props.error && (
      <Message type="error">{this.props.error.errorMessage || this.props.error.statusText}</Message>
    );

    const loading = this.state.loading && <LoadingIndicator />;

    const empty = !loading &&
      this.state.entities.length === 0 && (
        <NoEntities label="Report" createFunction={this.createReport} />
      );

    const search = !loading &&
      !empty && (
        <Input className="searchInput" placeholder="Filter for name" onChange={this.updateSearch} />
      );

    return (
      <div className="Reports">
        <h1>
          <HeaderIcon /> Reports
        </h1>
        <div className="createButton">
          <Button color="green" onClick={this.createReport}>
            Create BPMN Report
          </Button>
          <Dropdown label={<Icon type="down" />}>
            <Dropdown.Option onClick={this.createReport}>Create BPMN Report</Dropdown.Option>
            <Dropdown.Option onClick={this.createCombinedReport}>
              Create Combined BPMN Report
            </Dropdown.Option>
            <Dropdown.Option onClick={this.createDMNReport}>Create DMN Report</Dropdown.Option>
          </Dropdown>
        </div>
        {error}
        {search}
        {loading}
        <ul className="entityList">
          {empty}
          {this.state.entities
            .filter(({name}) => name.toLowerCase().includes(this.state.search.toLowerCase()))
            .map((itemData, idx) => {
              const {Icon: ReportIcon, label} = getReportIcon(itemData);

              return (
                <li key={idx}>
                  <Link className="info" to={`/report/${itemData.id}`}>
                    <span className="icon" title={label}>
                      <ReportIcon />
                    </span>
                    <div className="textInfo">
                      <div className="data dataTitle">
                        <h3>{formatters.getHighlightedText(itemData.name, this.state.search)}</h3>
                        {itemData.combined && <span>Combined</span>}
                        {itemData.reportType &&
                          itemData.reportType === 'decision' && <span>DMN</span>}
                      </div>
                      <div className="extraInfo">
                        <span className="data custom">{getReportInfo(itemData)}</span>
                        <LastModified date={itemData.lastModified} author={itemData.lastModifier} />
                      </div>
                    </div>
                  </Link>
                  <div className="operations">
                    <Link title="Edit Report" to={`/report/${itemData.id}/edit`}>
                      <Icon title="Edit Report" type="edit" className="editLink" />
                    </Link>
                    <Button title="Duplicate Report" onClick={this.duplicateReport(itemData)}>
                      <Icon
                        type="copy-document"
                        title="Duplicate Report"
                        className="duplicateIcon"
                      />
                    </Button>
                    <Button title="Delete Report" onClick={this.showDeleteModalFor(itemData)}>
                      <Icon type="delete" title="Delete Report" className="deleteIcon" />
                    </Button>
                  </div>
                </li>
              );
            })}
        </ul>
        <ConfirmationModal
          open={this.state.deleting !== false}
          onClose={this.hideDeleteModal}
          onConfirm={this.deleteReport}
          entityName={this.state.deleting && this.state.deleting.name}
          conflict={{type: 'Delete', items: this.state.conflicts}}
        />
      </div>
    );
  }
}

export default withErrorHandling(Reports);
