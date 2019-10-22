/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import moment from 'moment';
import {Link, Redirect} from 'react-router-dom';

import {t} from 'translation';
import {LoadingIndicator, Icon, Dropdown, ConfirmationModal, Input} from 'components';
import {showError} from 'notifications';
import {checkDeleteConflict, deleteEntity, createEntity, updateEntity} from 'services';
import {withErrorHandling} from 'HOC';

import CreateNewButton from './CreateNewButton';
import CollectionModal from './CollectionModal';
import CopyModal from './CopyModal';
import ListItem from './ListItem';

import {ReactComponent as ReportIcon} from './icons/report.svg';
import {ReactComponent as DashboardIcon} from './icons/dashboard.svg';
import {ReactComponent as CollectionIcon} from './icons/collection.svg';

import {copyEntity} from './service';

import './EntityList.scss';

export default withErrorHandling(
  class EntityList extends React.Component {
    state = {
      deleting: null,
      conflictedItems: null,
      copying: null,
      redirect: false,
      deleteInProgress: false,
      creatingCollection: false,
      editingCollection: null,
      searchQuery: ''
    };

    confirmDelete = entity => {
      const {entityType, id} = entity;
      this.setState({deleting: entity});
      if (entityType === 'report') {
        this.props.mightFail(
          checkDeleteConflict(id, entityType),
          ({conflictedItems}) => {
            this.setState({conflictedItems});
          },
          error => {
            showError(error);
            this.setState({conflictedItems: []});
          }
        );
      } else {
        this.setState({conflictedItems: []});
      }
    };

    resetDelete = () =>
      this.setState({deleting: null, conflictedItems: null, deleteInProgress: false});

    deleteEntity = () => {
      const {entityType, id} = this.state.deleting;

      this.setState({deleteInProgress: true});
      this.props.mightFail(
        deleteEntity(entityType, id),
        () => {
          this.resetDelete();
          if (typeof this.props.onChange === 'function') {
            this.props.onChange();
          }
        },
        error => {
          showError(error);
          this.setState({deleteInProgress: false});
        }
      );
    };

    startCreatingCollection = () => this.setState({creatingCollection: true});
    stopCreatingCollection = () => this.setState({creatingCollection: false});

    openCopyModal = entity => this.setState({copying: entity});
    closeCopyModal = () => this.setState({copying: null});

    copyEntity = (name, destination, redirect) => {
      const {id, entityType} = this.state.copying;
      this.props.mightFail(
        copyEntity(entityType, id, name, destination),
        () => {
          if (redirect) {
            this.setState({redirect: destination ? `/collection/${destination}/` : '/'});
          } else {
            this.props.onChange();
          }
        },
        showError
      );
      this.closeCopyModal();
    };

    componentDidUpdate() {
      if (this.state.redirect) {
        this.setState({redirect: false});
      }
    }

    startEditingCollection = editingCollection => {
      this.setState({editingCollection});
    };
    stopEditingCollection = () => {
      this.setState({editingCollection: null});
    };

    render() {
      const {
        deleting,
        copying,
        redirect,
        conflictedItems,
        deleteInProgress,
        creatingCollection,
        editingCollection,
        searchQuery
      } = this.state;

      const {collection, children, readOnly} = this.props;

      if (redirect) {
        return <Redirect to={redirect} />;
      }

      return (
        <div className="EntityList">
          <div className="header">
            <h1>{collection ? t('home.collectionTitle') : t('home.title')}</h1>
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
              <CreateNewButton
                createCollection={this.startCreatingCollection}
                collection={this.props.collection}
              />
            )}
          </div>
          <div className="content">
            <ul>{this.renderList()}</ul>
            {children}
          </div>
          <ConfirmationModal
            open={deleting}
            onClose={this.resetDelete}
            onConfirm={this.deleteEntity}
            entityName={deleting && deleting.name}
            conflict={{type: 'delete', items: conflictedItems}}
            loading={conflictedItems === null || deleteInProgress}
          />
          {creatingCollection && (
            <CollectionModal
              title={t('common.collection.modal.title.new')}
              initialName={t('common.collection.modal.defaultName')}
              confirmText={t('common.collection.modal.createBtn')}
              onClose={this.stopCreatingCollection}
              onConfirm={name => createEntity('collection', {name})}
            />
          )}
          {editingCollection && (
            <CollectionModal
              title={t('common.collection.modal.title.edit')}
              initialName={editingCollection.name}
              confirmText={t('common.collection.modal.editBtn')}
              onClose={this.stopEditingCollection}
              onConfirm={async name => {
                await updateEntity('collection', editingCollection.id, {name});
                this.stopEditingCollection();
                this.props.onChange();
              }}
            />
          )}
          {copying && (
            <CopyModal
              entity={copying}
              collection={collection || null}
              onClose={this.closeCopyModal}
              onConfirm={this.copyEntity}
            />
          )}
        </div>
      );
    }

    renderList() {
      if (this.props.data === null) {
        return <LoadingIndicator />;
      }

      if (this.props.data.length === 0) {
        return <div className="empty">{t('home.empty')}</div>;
      }

      const searchFilteredData = this.props.data.filter(({name}) =>
        name.toLowerCase().includes(this.state.searchQuery.toLowerCase())
      );

      if (searchFilteredData.length === 0) {
        return <div className="empty">{t('common.notFound')}</div>;
      }

      return searchFilteredData.map(entity => {
        const {
          id,
          entityType,
          currentUserRole,
          lastModified,
          name,
          data,
          reportType,
          combined
        } = entity;

        const canEdit =
          (entityType !== 'collection' && currentUserRole === 'editor') ||
          currentUserRole === 'manager';

        return (
          <ListItem key={id} className={entityType}>
            <Link to={formatLink(id, entityType)}>
              <ListItem.Section className="icon">{getEntityIcon(entityType)}</ListItem.Section>
              <ListItem.Section className="name">
                <div className="type">{formatType(entityType, reportType, combined)}</div>
                <div className="entityName" title={name}>
                  {name}
                </div>
              </ListItem.Section>
              <ListItem.Section className="containedEntities">
                {formatSubEntities(data.subEntityCounts)}
              </ListItem.Section>
              <ListItem.Section className="modifiedDate">
                {moment(lastModified).format('YYYY-MM-DD HH:mm')}
              </ListItem.Section>
              <ListItem.Section className="users">
                {formatUserCount(data.roleCounts)}
              </ListItem.Section>
            </Link>
            <div className="contextMenu">
              {(canEdit || entityType !== 'collection') && (
                <Dropdown label={<Icon type="overflow-menu-vertical" size="24px" />}>
                  {canEdit && (
                    <Dropdown.Option
                      {...(entityType === 'collection'
                        ? {
                            onClick: () => this.startEditingCollection(entity)
                          }
                        : {
                            link: formatLink(id, entityType) + 'edit'
                          })}
                    >
                      <Icon type="edit" />
                      {t('common.edit')}
                    </Dropdown.Option>
                  )}
                  {entityType !== 'collection' && (
                    <Dropdown.Option onClick={() => this.openCopyModal(entity)}>
                      <Icon type="copy-document" />
                      {t('common.copy')}
                    </Dropdown.Option>
                  )}
                  {canEdit && (
                    <Dropdown.Option onClick={() => this.confirmDelete(entity)}>
                      <Icon type="delete" />
                      {t('common.delete')}
                    </Dropdown.Option>
                  )}
                </Dropdown>
              )}
            </div>
          </ListItem>
        );
      });
    }
  }
);

function formatLink(id, type) {
  return `${type}/${id}/`;
}

function getEntityIcon(type) {
  switch (type) {
    case 'collection':
      return <CollectionIcon />;
    case 'dashboard':
      return <DashboardIcon />;
    case 'report':
      return <ReportIcon />;
    default:
      return <ReportIcon />;
  }
}

function formatSubEntities({dashboard, report}) {
  let string = '';
  if (dashboard) {
    string += dashboard + ' ';
    string += t('dashboard.' + (dashboard > 1 ? 'label-plural' : 'label'));
    if (report) {
      string += ', ';
    }
  }
  if (report) {
    string += report + ' ';
    string += t('report.' + (report > 1 ? 'label-plural' : 'label'));
  }

  return string;
}

function formatUserCount({user, group}) {
  let string = '';
  if (group) {
    string += group + ' ';
    string += t('common.user-group.' + (group > 1 ? 'label-plural' : 'label'));
    if (user) {
      string += ', ';
    }
  }
  if (user) {
    string += user + ' ';
    string += t('common.user.' + (user > 1 ? 'label-plural' : 'label'));
  }

  return string;
}

function formatType(entityType, reportType, combined) {
  switch (entityType) {
    case 'collection':
      return t('common.collection.label');
    case 'dashboard':
      return t('dashboard.label');
    case 'report':
      if (reportType === 'process' && !combined) {
        return t('home.types.process');
      }
      if (reportType === 'process' && combined) {
        return t('home.types.combined');
      }
      if (reportType === 'decision') {
        return t('home.types.decision');
      }
      return t('report.label');
    default:
      return t('home.types.unknown');
  }
}
