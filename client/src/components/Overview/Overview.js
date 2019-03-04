import React, {Component} from 'react';

import {withErrorHandling} from 'HOC';
import './Overview.scss';
import Reports from './Reports';
import Dashboards from './Dashboards';
import Collections from './Collections';
import {Redirect} from 'react-router-dom';
import {checkDeleteConflict} from 'services';

import {ConfirmationModal, Button, Dropdown, Icon, Message, LoadingIndicator} from 'components';

import {
  loadCollections,
  loadReports,
  loadDashboards,
  createCollection,
  deleteCollection,
  updateCollection,
  createReport,
  deleteReport,
  createDashboard,
  deleteDashboard
} from './service';

class Overview extends Component {
  state = {
    loading: true,
    redirect: false,
    deleting: false,
    collections: [],
    reports: [],
    dashboards: [],
    updating: null,
    conflicts: []
  };

  async componentDidMount() {
    await this.loadData();
  }

  loadData = async () => {
    this.props.mightFail(
      await Promise.all([loadCollections(), loadReports(), loadDashboards()]),
      ([collections, reports, dashboards]) => {
        this.setState({collections, reports, dashboards, loading: false});
      }
    );
  };

  createCombinedReport = async () =>
    this.setState({
      redirect: '/report/' + (await createReport({combined: true, reportType: 'process'}))
    });
  createProcessReport = async () =>
    this.setState({
      redirect: '/report/' + (await createReport({combined: false, reportType: 'process'}))
    });
  createDecisionReport = async () =>
    this.setState({
      redirect: '/report/' + (await createReport({combined: false, reportType: 'decision'}))
    });

  createDashboard = async () =>
    this.setState({redirect: '/dashboard/' + (await createDashboard())});

  updateOrCreateCollection = async collection => {
    const editCollection = this.state.updating;
    if (editCollection.id) {
      await updateCollection(editCollection.id, collection);
    } else {
      await createCollection(collection);
    }
    this.setState({updating: null});

    this.loadData([loadCollections()]);
  };

  deleteEntity = async () => {
    const {type, entity} = this.state.deleting;

    switch (type) {
      case 'reports':
        await deleteReport(entity.id);
        break;
      case 'dashboards':
        await deleteDashboard(entity.id);
        break;
      default:
        await deleteCollection(entity.id);
    }
    this.setState({
      deleting: false,
      conflicts: []
    });
    this.loadData();
  };

  duplicateReport = report => async evt => {
    evt.target.blur();

    const copy = {
      ...report,
      name: report.name + ' - Copy'
    };
    await createReport(copy);
    this.loadData();
  };

  duplicateDashboard = dashboard => async evt => {
    evt.target.blur();

    const copy = {
      ...dashboard,
      name: dashboard.name + ' - Copy'
    };
    await createDashboard(copy);
    this.loadData();
  };

  setEntityToUpdate = updating => this.setState({updating});

  showReportDeleteModal = async deleting => {
    const {conflictedItems} = await checkDeleteConflict(deleting.entity.id);
    this.setState({deleting: deleting, conflicts: conflictedItems});
  };

  showDeleteModalFor = deleting => async () => {
    if (deleting.type === 'reports') return await this.showReportDeleteModal(deleting);
    this.setState({deleting});
  };

  hideDeleteModal = () => this.setState({deleting: false, conflicts: []});

  render() {
    const {
      loading,
      reports,
      dashboards,
      collections,
      redirect,
      updating,
      deleting,
      conflicts
    } = this.state;

    if (redirect) {
      return <Redirect to={`${redirect}/edit?new`} />;
    }

    if (loading) {
      return <LoadingIndicator />;
    }

    const error = this.props.error && (
      <Message type="error">{this.props.error.errorMessage || this.props.error.statusText}</Message>
    );

    return (
      <div className="Overview">
        {error}
        <div className="header">
          <div className="createButton">
            <Dropdown
              label={
                <>
                  Create New <Icon type="down" />
                </>
              }
            >
              <Dropdown.Option onClick={() => this.setEntityToUpdate({})}>
                Create Collection
              </Dropdown.Option>
              <Dropdown.Option onClick={this.createDashboard}>Create Dashboard</Dropdown.Option>
              <Dropdown.Submenu label="New Report">
                <Dropdown.Option onClick={this.createProcessReport}>
                  Create Process Report
                </Dropdown.Option>
                <Dropdown.Option onClick={this.createCombinedReport}>
                  Create Combined Process Report
                </Dropdown.Option>
                <Dropdown.Option onClick={this.createDecisionReport}>
                  Create Decision Report
                </Dropdown.Option>
              </Dropdown.Submenu>
            </Dropdown>
          </div>
        </div>
        <Collections
          collections={collections}
          updating={updating}
          duplicateReport={this.duplicateReport}
          updateOrCreateCollection={this.updateOrCreateCollection}
          setCollectionToUpdate={this.setEntityToUpdate}
          showDeleteModalFor={this.showDeleteModalFor}
        />
        <Button color="green" className="createButton" onClick={this.createDashboard}>
          Create New Dashboard
        </Button>
        <Dashboards
          dashboards={dashboards}
          duplicateDashboard={this.duplicateDashboard}
          createDashboard={this.createDashboard}
          showDeleteModalFor={this.showDeleteModalFor}
        />
        <div className="createButton">
          <Button color="green" onClick={this.createProcessReport}>
            Create Process Report
          </Button>
          <Dropdown label={<Icon type="down" />}>
            <Dropdown.Option onClick={this.createProcessReport}>
              Create Process Report
            </Dropdown.Option>
            <Dropdown.Option onClick={this.createCombinedReport}>
              Create Combined Process Report
            </Dropdown.Option>
            <Dropdown.Option onClick={this.createDecisionReport}>
              Create Decision Report
            </Dropdown.Option>
          </Dropdown>
        </div>
        <Reports
          reports={reports}
          createProcessReport={this.createProcessReport}
          duplicateReport={this.duplicateReport}
          showDeleteModalFor={this.showDeleteModalFor}
        />
        <ConfirmationModal
          open={deleting !== false}
          onClose={this.hideDeleteModal}
          onConfirm={this.deleteEntity}
          entityName={deleting && deleting.entity.name}
          conflict={{type: 'Delete', items: conflicts}}
        />
      </div>
    );
  }
}

export default withErrorHandling(Overview);
