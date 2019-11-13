/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {t} from 'translation';
import {LoadingIndicator, Icon, Dropdown, Input, ConfirmationModal, Button} from 'components';
import {showError} from 'notifications';
import {withErrorHandling} from 'HOC';

import ListItem from './ListItem';

import {getSources, addSource, editSource, removeSource} from './service';

import {ReactComponent as ProcessIcon} from './icons/process.svg';
import {ReactComponent as DecisionIcon} from './icons/decision.svg';
import AddSourceModal from './modals/AddSourceModal';
import EditSourceModal from './modals/EditSourceModal';

import './SourcesList.scss';

export default withErrorHandling(
  class SourcesList extends React.Component {
    state = {
      sources: null,
      deleting: null,
      deleteInProgress: false,
      searchQuery: '',
      addingSource: false
    };

    componentDidMount() {
      this.getSources();
    }

    getSources = () => {
      this.props.mightFail(
        getSources(this.props.collection),
        sources => this.setState({sources}),
        showError
      );
    };

    confirmDelete = entity => {
      this.setState({deleting: entity});
    };

    resetDelete = () => this.setState({deleting: null, deleteInProgress: false});

    deleteSource = () => {
      const {id} = this.state.deleting;
      this.resetDelete();
      this.setState({deleteInProgress: true});
      this.props.mightFail(
        removeSource(this.props.collection, id),
        () => {
          this.getSources();
          this.setState({deleteInProgress: false});
        },
        error => {
          showError(error);
          this.setState({deleteInProgress: false});
        }
      );
    };

    openAddSourceModal = () => this.setState({addingSource: true});
    addSource = source => {
      this.closeAddSourceModal();
      this.props.mightFail(addSource(this.props.collection, source), this.getSources, showError);
    };
    closeAddSourceModal = () => this.setState({addingSource: false});

    openEditSourceModal = editing => this.setState({editing});
    editSource = tenants => {
      this.props.mightFail(
        editSource(this.props.collection, this.state.editing.id, tenants),
        this.getSources,
        showError
      );
      this.closeEditSourceModal();
    };
    closeEditSourceModal = () => this.setState({editing: null});

    render() {
      const {deleting, editing, deleteInProgress, searchQuery, addingSource} = this.state;
      const {readOnly} = this.props;

      return (
        <div className="SourcesList">
          <div className="header">
            <h1>{t('home.sources.title')}</h1>
            <div className="searchContainer">
              <Icon className="searchIcon" type="search" />
              <Input
                required
                type="text"
                className="searchInput"
                placeholder={t('home.search.name')}
                value={searchQuery}
                onChange={({target: {value}}) => this.setState({searchQuery: value})}
                onClear={() => this.setState({searchQuery: ''})}
              />
            </div>
            {!readOnly && (
              <Button onClick={() => this.setState({addingSource: true})}>{t('common.add')}</Button>
            )}
          </div>
          <div className="content">
            <ul>{this.renderList()}</ul>
          </div>
          <ConfirmationModal
            open={deleting}
            onClose={this.resetDelete}
            onConfirm={this.deleteSource}
            entityName={deleting && (deleting.definitionName || deleting.definitionKey)}
            loading={deleteInProgress}
          />
          <AddSourceModal
            open={addingSource}
            onClose={this.closeAddSourceModal}
            onConfirm={this.addSource}
          />
          {editing && (
            <EditSourceModal
              source={editing}
              onClose={this.closeEditSourceModal}
              onConfirm={this.editSource}
            />
          )}
        </div>
      );
    }

    renderList() {
      const {readOnly} = this.props;
      const {sources} = this.state;

      if (sources === null) {
        return <LoadingIndicator />;
      }

      if (sources.length === 0) {
        return <div className="empty">{t('home.sources.notCreated')}</div>;
      }

      const searchFilteredData = sources.filter(({definitionName, definitionKey}) =>
        (definitionName || definitionKey)
          .toLowerCase()
          .includes(this.state.searchQuery.toLowerCase())
      );

      if (searchFilteredData.length === 0) {
        return <div className="empty">{t('common.notFound')}</div>;
      }

      return searchFilteredData.map(source => {
        const {definitionKey, definitionName, definitionType, tenants} = source;

        return (
          <ListItem key={definitionKey} className={definitionType}>
            <ListItem.Section className="icon">{getSourceIcon(definitionType)}</ListItem.Section>
            <ListItem.Section className="name">
              <div className="type">{formatType(definitionType)}</div>
              <div className="entityName">{definitionName || definitionKey}</div>
            </ListItem.Section>
            <ListItem.Section className="tenants">{formatTenants(tenants)}</ListItem.Section>
            {!readOnly && (
              <div className="contextMenu">
                <Dropdown label={<Icon type="overflow-menu-vertical" size="24px" />}>
                  <Dropdown.Option onClick={() => this.openEditSourceModal(source)}>
                    <Icon type="edit" />
                    {t('common.edit')}
                  </Dropdown.Option>
                  <Dropdown.Option onClick={() => this.confirmDelete(source)}>
                    <Icon type="delete" />
                    {t('common.delete')}
                  </Dropdown.Option>
                </Dropdown>
              </div>
            )}
          </ListItem>
        );
      });
    }
  }
);

function getSourceIcon(type) {
  switch (type) {
    case 'process':
      return <ProcessIcon />;
    case 'decision':
      return <DecisionIcon />;
    default:
      return <ProcessIcon />;
  }
}

function formatType(type) {
  switch (type) {
    case 'process':
      return t('home.sources.process');
    case 'decision':
      return t('home.sources.decision');
    default:
      return t('home.types.unknown');
  }
}

function formatTenants(tenants) {
  return tenants.map(({name}) => name).join(', ');
}
