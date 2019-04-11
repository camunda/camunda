/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component} from 'react';

import {Redirect} from 'react-router-dom';
import {
  loadEntities,
  deleteEntity,
  createEntity,
  updateEntity,
  checkDeleteConflict,
  getEntitiesCollections,
  toggleEntityCollection
} from 'services';
import {withErrorHandling} from 'HOC';
const OverviewContext = React.createContext();

class OverviewStore extends Component {
  state = {
    loading: true,
    redirect: false,
    deleting: false,
    collections: [],
    reports: [],
    dashboards: [],
    updating: null,
    conflicts: [],
    deleteLoading: false,
    searchQuery: ''
  };

  async componentDidMount() {
    await this.loadData();
  }

  loadData = async () => {
    this.props.mightFail(
      await Promise.all([
        loadEntities('collection', 'created'),
        loadEntities('report', 'lastModified'),
        loadEntities('dashboard', 'lastModified')
      ]),
      ([collections, reports, dashboards]) => {
        this.setState({collections, reports, dashboards, loading: false});
      }
    );
  };

  filter = searchQuery => {
    this.setState({searchQuery});
  };

  createCombinedReport = async () =>
    this.setState({
      redirect:
        '/report/' + (await createEntity('report', null, {combined: true, reportType: 'process'}))
    });
  createProcessReport = async () =>
    this.setState({
      redirect:
        '/report/' + (await createEntity('report', null, {combined: false, reportType: 'process'}))
    });
  createDecisionReport = async () =>
    this.setState({
      redirect:
        '/report/' + (await createEntity('report', null, {combined: false, reportType: 'decision'}))
    });

  createDashboard = async () =>
    this.setState({redirect: '/dashboard/' + (await createEntity('dashboard'))});

  updateOrCreateCollection = async collection => {
    const editCollection = this.state.updating;
    if (editCollection.id) {
      await updateEntity('collection', editCollection.id, collection);
    } else {
      await createEntity('collection', collection);
    }
    this.setState({updating: null});

    this.loadData();
  };

  deleteEntity = async () => {
    const {type, entity} = this.state.deleting;

    this.setState({deleteLoading: true});

    await deleteEntity(type, entity.id);

    this.setState({
      deleting: false,
      deleteLoading: false,
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
      id = await createEntity(type, copy, {combined: copy.combined, reportType: copy.reportType});
    } else {
      id = await createEntity(type, copy);
    }

    if (collection) {
      toggleEntityCollection(this.loadData)({id}, collection, false);
    } else {
      this.loadData();
    }
  };

  setCollectionToUpdate = updating => this.setState({updating});

  showDeleteModalFor = deleting => async () => {
    this.setState({deleting, deleteLoading: true});
    if (deleting.type !== 'collection') {
      const {conflictedItems} = await checkDeleteConflict(deleting.entity.id, deleting.type);
      this.setState({conflicts: conflictedItems});
    }
    this.setState({deleteLoading: false});
  };

  hideDeleteModal = () => this.setState({deleting: false, conflicts: []});

  render() {
    const {redirect} = this.state;
    const entitiesCollections = getEntitiesCollections(this.state.collections);

    if (redirect) {
      return <Redirect to={`${redirect}/edit?new`} />;
    }

    const {
      createCombinedReport,
      createProcessReport,
      createDecisionReport,
      createDashboard,
      updateOrCreateCollection,
      deleteEntity,
      duplicateEntity,
      setCollectionToUpdate,
      showDeleteModalFor,
      hideDeleteModal,
      filter,
      state
    } = this;

    const contextValue = {
      createCombinedReport,
      createProcessReport,
      createDecisionReport,
      createDashboard,
      updateOrCreateCollection,
      duplicateEntity,
      deleteEntity,
      showDeleteModalFor,
      hideDeleteModal,
      setCollectionToUpdate,
      toggleEntityCollection: toggleEntityCollection(this.loadData),
      filter,
      store: state,
      entitiesCollections,
      error: this.props.error
    };

    return (
      <OverviewContext.Provider value={contextValue}>
        {this.props.children}
      </OverviewContext.Provider>
    );
  }
}

export const StoreProvider = withErrorHandling(OverviewStore);

export const withStore = Component => {
  function WithStore(props) {
    return (
      <OverviewContext.Consumer>
        {overviewProps => <Component {...props} {...overviewProps} />}
      </OverviewContext.Consumer>
    );
  }

  WithStore.WrappedComponent = Component;

  return WithStore;
};
