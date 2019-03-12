import React, {Component} from 'react';

import {withErrorHandling} from 'HOC';
import './Overview.scss';
import Reports from './Reports';
import Dashboards from './Dashboards';
import Collections from './Collections';
import {Redirect} from 'react-router-dom';
import CollectionsDropdown from './subComponents/CollectionsDropdown';
import {checkDeleteConflict} from 'services';
import {getEntitiesCollections} from './service';

import {ConfirmationModal, Button, Dropdown, Icon, Message, LoadingIndicator} from 'components';

import {load, remove, create, update} from './service';

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
      await Promise.all([load('collection'), load('report'), load('dashboard')]),
      ([collections, reports, dashboards]) => {
        this.setState({collections, reports, dashboards, loading: false});
      }
    );
  };

  createCombinedReport = async () =>
    this.setState({
      redirect: '/report/' + (await create('report', null, {combined: true, reportType: 'process'}))
    });
  createProcessReport = async () =>
    this.setState({
      redirect:
        '/report/' + (await create('report', null, {combined: false, reportType: 'process'}))
    });
  createDecisionReport = async () =>
    this.setState({
      redirect:
        '/report/' + (await create('report', null, {combined: false, reportType: 'decision'}))
    });

  createDashboard = async () =>
    this.setState({redirect: '/dashboard/' + (await create('dashboard'))});

  updateOrCreateCollection = async collection => {
    const editCollection = this.state.updating;
    if (editCollection.id) {
      await update('collection', editCollection.id, collection);
    } else {
      await create('collection', collection);
    }
    this.setState({updating: null});

    this.loadData();
  };

  deleteEntity = async () => {
    const {type, entity} = this.state.deleting;

    await remove(type, entity.id);

    this.setState({
      deleting: false,
      conflicts: []
    });
    this.loadData();
  };

  duplicateEntity = (type, entity, collection) => async evt => {
    evt.target.blur();

    const copy = {
      ...entity,
      name: entity.name + ' - Copy'
    };

    let id;
    if (type === 'report') {
      id = await create(type, copy, {combined: copy.combined, reportType: copy.reportType});
    } else {
      id = await create(type, copy);
    }

    if (collection) {
      this.toggleEntityCollection({id}, collection, false)();
    } else {
      this.loadData();
    }
  };

  setEntityToUpdate = updating => this.setState({updating});

  showDeleteModalFor = deleting => async () => {
    const deleteState = {deleting};
    if (deleting.type !== 'collection') {
      const {conflictedItems} = await checkDeleteConflict(deleting.entity.id, deleting.type);
      deleteState.conflicts = conflictedItems;
    }
    this.setState(deleteState);
  };

  hideDeleteModal = () => this.setState({deleting: false, conflicts: []});

  toggleEntityCollection = (entity, collection, isRemove) => async evt => {
    const collectionEntitiesIds = collection.data.entities.map(entity => entity.id);

    const change = {data: {}};
    if (isRemove) {
      change.data.entities = collectionEntitiesIds.filter(id => id !== entity.id);
    } else {
      change.data.entities = [...collectionEntitiesIds, entity.id];
    }

    await update('collection', collection.id, change);
    await this.loadData();
  };

  renderCollectionsDropdown = entitiesCollections => (currentEntity, currentCollection) => {
    return (
      <CollectionsDropdown
        currentCollection={currentCollection}
        entity={currentEntity}
        entityCollections={entitiesCollections[currentEntity.id]}
        collections={this.state.collections}
        toggleEntityCollection={this.toggleEntityCollection}
      />
    );
  };

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

    const entitiesCollections = getEntitiesCollections(collections);

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
          duplicateEntity={this.duplicateEntity}
          updateOrCreateCollection={this.updateOrCreateCollection}
          setCollectionToUpdate={this.setEntityToUpdate}
          showDeleteModalFor={this.showDeleteModalFor}
          renderCollectionsDropdown={this.renderCollectionsDropdown(entitiesCollections)}
        />
        <Button color="green" className="createButton" onClick={this.createDashboard}>
          Create New Dashboard
        </Button>
        <Dashboards
          dashboards={dashboards}
          duplicateEntity={this.duplicateEntity}
          createDashboard={this.createDashboard}
          showDeleteModalFor={this.showDeleteModalFor}
          renderCollectionsDropdown={this.renderCollectionsDropdown(entitiesCollections)}
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
          duplicateEntity={this.duplicateEntity}
          showDeleteModalFor={this.showDeleteModalFor}
          renderCollectionsDropdown={this.renderCollectionsDropdown(entitiesCollections)}
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
