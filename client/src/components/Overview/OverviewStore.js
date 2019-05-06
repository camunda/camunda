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

import {addNotification} from 'notifications';

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

  componentDidMount() {
    this.loadData();
  }

  showError = async error => {
    let text = error;

    if (typeof error.json === 'function') {
      text = (await error.json()).errorMessage;
    } else if (error.message) {
      text = error.message;
    }

    addNotification({type: 'error', text});
    this.setState({loading: false, deleteLoading: false});
  };

  loadData = () => {
    this.props.mightFail(
      Promise.all([
        loadEntities('collection', 'created'),
        loadEntities('report', 'lastModified'),
        loadEntities('dashboard', 'lastModified')
      ]),
      ([collections, reports, dashboards]) => {
        this.setState({collections, reports, dashboards, loading: false});
      },
      this.showError
    );
  };

  filter = searchQuery => {
    this.setState({searchQuery});
  };

  createReport = (type, subType) => () =>
    this.props.mightFail(
      createEntity(`report/${type}/${subType}`),
      id => this.setState({redirect: '/report/' + id}),
      this.showError
    );

  createCombinedReport = this.createReport('process', 'combined');
  createProcessReport = this.createReport('process', 'single');
  createDecisionReport = this.createReport('decision', 'single');

  createDashboard = () =>
    this.props.mightFail(
      createEntity('dashboard'),
      id => this.setState({redirect: '/dashboard/' + id}),
      this.showError
    );

  finishCollectionUpdate = () => {
    this.setState({updating: null});

    this.loadData();
  };

  updateOrCreateCollection = collection => {
    const editCollection = this.state.updating;
    if (editCollection.id) {
      this.props.mightFail(
        updateEntity('collection', editCollection.id, collection),
        this.finishCollectionUpdate,
        this.showError
      );
    } else {
      this.props.mightFail(
        createEntity('collection', collection),
        this.finishCollectionUpdate,
        this.showError
      );
    }
  };

  deleteEntity = () => {
    const {type, entity} = this.state.deleting;

    this.setState({deleteLoading: true});

    this.props.mightFail(
      deleteEntity(type, entity.id),
      () => {
        this.setState({
          deleting: false,
          deleteLoading: false,
          conflicts: []
        });
        this.loadData();
      },
      this.showError
    );
  };

  duplicateEntity = (type, entity, collection) => evt => {
    evt.target.blur();

    const copy = {
      ...entity,
      name: entity.name + ' - Copy'
    };

    const applyCollections = id => {
      if (collection) {
        toggleEntityCollection(this.loadData)({id}, collection, false);
      } else {
        this.loadData();
      }
    };

    if (type === 'report') {
      const {combined, reportType} = copy;
      this.props.mightFail(
        createEntity(`report/${reportType}/${combined ? 'combined' : 'single'}`, copy),
        applyCollections,
        this.showError
      );
    } else {
      this.props.mightFail(createEntity(type, copy), applyCollections, this.showError);
    }
  };

  setCollectionToUpdate = updating => this.setState({updating});

  showDeleteModalFor = deleting => () => {
    this.setState({deleting, deleteLoading: true});
    if (deleting.type !== 'collection') {
      this.props.mightFail(
        checkDeleteConflict(deleting.entity.id, deleting.type),
        ({conflictedItems}) => {
          this.setState({conflicts: conflictedItems, deleteLoading: false});
        },
        this.showError
      );
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
      entitiesCollections
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
