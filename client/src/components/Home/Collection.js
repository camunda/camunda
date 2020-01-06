/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import moment from 'moment';
import {Link, Redirect} from 'react-router-dom';
import classnames from 'classnames';

import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {Icon, Dropdown, EntityList, Deleter} from 'components';
import {loadEntity, updateEntity, checkDeleteConflict} from 'services';
import {showError, addNotification} from 'notifications';
import {refreshBreadcrumbs} from 'components/navigation';
import Copier from './Copier';
import CreateNewButton from './CreateNewButton';

import {ReactComponent as CollectionIcon} from './icons/collection.svg';

import UserList from './UserList';
import AlertList from './AlertList';
import SourcesList from './SourcesList';
import CollectionModal from './modals/CollectionModal';

import {
  formatLink,
  formatType,
  formatSubEntities,
  formatUserCount,
  getEntityIcon
} from './formatters';

import './Collection.scss';

export default withErrorHandling(
  class Collection extends React.Component {
    state = {
      collection: null,
      editingCollection: false,
      deleting: false,
      redirect: '',
      copying: null
    };

    componentDidMount() {
      this.loadCollection();
    }

    componentDidUpdate(prevProps) {
      if (prevProps.match.params.id !== this.props.match.params.id) {
        this.setState({redirect: null});
        this.loadCollection();
      }
    }

    loadCollection = () => {
      this.props.mightFail(
        loadEntity('collection', this.props.match.params.id),
        collection => this.setState({collection}),
        error => {
          showError(error);
          this.setState({collection: null});
        }
      );
    };

    startEditingCollection = () => {
      this.setState({editingCollection: true});
    };
    stopEditingCollection = () => {
      this.setState({editingCollection: false});
    };

    render() {
      const {collection, deleting, editingCollection, redirect, copying} = this.state;

      const homeTab = this.props.match.params.viewMode === undefined;
      const userTab = this.props.match.params.viewMode === 'users';
      const alertTab = this.props.match.params.viewMode === 'alerts';
      const sourcesTab = this.props.match.params.viewMode === 'sources';

      if (redirect) {
        return <Redirect to={redirect} />;
      }

      const collectionEntity = {...collection, entityType: 'collection'};

      return (
        <div className="Collection">
          <div className="header">
            <div className="indicator" />
            <div className="icon">
              <CollectionIcon />
            </div>
            <div className="text">
              <div className="type">{t('common.collection.label')}</div>
              <div className="name">
                {collection && (
                  <>
                    <span title={collection.name}>{collection.name}</span>
                    {collection.currentUserRole === 'manager' && (
                      <Dropdown label={<Icon type="overflow-menu-vertical" size="24px" />}>
                        <Dropdown.Option onClick={this.startEditingCollection}>
                          <Icon type="edit" />
                          {t('common.edit')}
                        </Dropdown.Option>
                        <Dropdown.Option onClick={() => this.setState({copying: collectionEntity})}>
                          <Icon type="copy-document" />
                          {t('common.copy')}
                        </Dropdown.Option>
                        <Dropdown.Option
                          onClick={() => this.setState({deleting: collectionEntity})}
                        >
                          <Icon type="delete" />
                          {t('common.delete')}
                        </Dropdown.Option>
                      </Dropdown>
                    )}
                  </>
                )}
              </div>
            </div>
            <ul className="navigation">
              <li className={classnames({active: homeTab})}>
                <Link to=".">{t('home.collectionTitleWithAmpersand')}</Link>
              </li>
              <li className={classnames({active: alertTab})}>
                <Link to="alerts">{t('alert.label-plural')}</Link>
              </li>
              <li className={classnames({active: userTab})}>
                <Link to="users">{t('common.user.label-plural')}</Link>
              </li>
              <li className={classnames({active: sourcesTab})}>
                <Link to="sources">{t('home.sources.title')}</Link>
              </li>
            </ul>
          </div>
          <div className="content">
            {homeTab && (
              <EntityList
                name={t('home.collectionTitle')}
                action={
                  collection &&
                  collection.currentUserRole !== 'viewer' && (
                    <CreateNewButton collection={collection.id} />
                  )
                }
                empty={t('home.empty')}
                isLoading={!collection}
                data={
                  collection &&
                  collection.data.entities.map(entity => {
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

                    const actions = [
                      {
                        icon: 'copy-document',
                        text: t('common.copy'),
                        action: () => this.setState({copying: entity})
                      }
                    ];

                    if (currentUserRole === 'editor') {
                      actions.unshift({
                        icon: 'edit',
                        text: t('common.edit'),
                        action: () => this.setState({redirect: formatLink(id, entityType) + 'edit'})
                      });
                      actions.push({
                        icon: 'delete',
                        text: t('common.delete'),
                        action: () => this.setState({deleting: entity})
                      });
                    }

                    return {
                      className: entityType,
                      link: formatLink(id, entityType),
                      icon: getEntityIcon(entityType),
                      type: formatType(entityType, reportType, combined),
                      name,
                      meta1: formatSubEntities(data.subEntityCounts),
                      meta2: moment(lastModified).format('YYYY-MM-DD HH:mm'),
                      meta3: formatUserCount(data.roleCounts),
                      actions
                    };
                  })
                }
              />
            )}
            {alertTab && collection && (
              <AlertList
                readOnly={collection.currentUserRole === 'viewer'}
                collection={collection.id}
              />
            )}
            {userTab && collection && (
              <UserList
                readOnly={collection.currentUserRole !== 'manager'}
                onChange={this.loadCollection}
                collection={collection.id}
              />
            )}
            {sourcesTab && collection && (
              <SourcesList
                onChange={this.loadCollection}
                readOnly={collection.currentUserRole !== 'manager'}
                collection={collection.id}
              />
            )}
          </div>
          {editingCollection && (
            <CollectionModal
              title={t('common.collection.modal.title.edit')}
              initialName={collection.name}
              confirmText={t('common.collection.modal.editBtn')}
              onClose={this.stopEditingCollection}
              onConfirm={async name => {
                await updateEntity('collection', collection.id, {name});
                this.loadCollection();
                this.stopEditingCollection();
                refreshBreadcrumbs();
              }}
            />
          )}
          <Deleter
            entity={deleting}
            type={deleting && deleting.entityType}
            onDelete={() => {
              if (deleting.entityType === 'collection') {
                this.setState({redirect: '/'});
              } else {
                this.loadCollection();
              }
            }}
            checkConflicts={async () => {
              const {entityType, id} = deleting;
              if (entityType === 'report') {
                return checkDeleteConflict(id, entityType);
              }
              return {conflictedItems: []};
            }}
            onClose={() => this.setState({deleting: null})}
          />
          <Copier
            entity={copying}
            collection={collection && collection.id}
            onCopy={(name, redirect) => {
              const entity = this.state.copying;
              if (!redirect && entity.entityType === 'collection') {
                addNotification({type: 'success', text: t('common.collection.created', {name})});
              }
              if (!redirect && entity.entityType !== 'collection') {
                this.loadCollection();
              }
              this.setState({copying: null});
            }}
            onCancel={() => this.setState({copying: null})}
          />
        </div>
      );
    }
  }
);
