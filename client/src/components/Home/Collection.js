/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Link, Redirect} from 'react-router-dom';
import classnames from 'classnames';

import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {Icon, Dropdown, ConfirmationModal} from 'components';
import {loadEntity, updateEntity, deleteEntity} from 'services';
import {showError} from 'notifications';

import {ReactComponent as CollectionIcon} from './icons/collection.svg';

import EntityList from './EntityList';
import UserList from './UserList';
import CollectionModal from './CollectionModal';

import './Collection.scss';

export default withErrorHandling(
  class Collection extends React.Component {
    state = {
      collection: null,
      editingCollection: false,
      deleting: false,
      goBack: false,
      deleteInProgress: false
    };

    componentDidMount() {
      this.loadCollection();
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

    confirmDelete = () => {
      this.setState({deleting: true});
    };

    deleteCollection = () => {
      this.setState({deleteInProgress: true});
      this.props.mightFail(
        deleteEntity('collection', this.state.collection.id),
        () => {
          this.setState({goBack: true});
        },
        error => {
          showError(error);
          this.setState({deleteInProgress: false});
        }
      );
    };

    resetDelete = () => {
      this.setState({deleting: false, deleteInProgress: false});
    };

    render() {
      const {collection, deleting, deleteInProgress, editingCollection, goBack} = this.state;

      const userTab = this.props.match.params.viewMode === 'users';

      if (goBack) {
        return <Redirect to="/" />;
      }

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
                {collection ? (
                  <>
                    {collection.name}
                    <Dropdown label={<Icon type="overflow-menu-vertical" size="24px" />}>
                      {collection.currentUserRole === 'manager' && (
                        <>
                          <Dropdown.Option onClick={this.startEditingCollection}>
                            <Icon type="edit" />
                            {t('common.edit')}
                          </Dropdown.Option>
                          <Dropdown.Option onClick={this.confirmDelete}>
                            <Icon type="delete" />
                            {t('common.delete')}
                          </Dropdown.Option>
                        </>
                      )}
                    </Dropdown>
                  </>
                ) : (
                  ''
                )}
              </div>
            </div>
            <ul className="navigation">
              <li className={classnames({active: !userTab})}>
                <Link to=".">{t('home.collectionTitleWithAmpersand')}</Link>
              </li>
              <li className={classnames({active: userTab})}>
                <Link to="users">{t('common.user.label-plural')}</Link>
              </li>
            </ul>
          </div>
          <div className="content">
            {!userTab && (
              <EntityList
                readOnly={!collection || collection.currentUserRole === 'viewer'}
                data={collection ? collection.data.entities : null}
                onChange={this.loadCollection}
                collection={collection ? collection.id : null}
              />
            )}
            {userTab && collection && (
              <UserList
                readOnly={collection.currentUserRole !== 'manager'}
                data={collection.data.roles}
                onChange={this.loadCollection}
                collection={collection.id}
              />
            )}
          </div>
          <ConfirmationModal
            open={deleting}
            onClose={this.resetDelete}
            onConfirm={this.deleteCollection}
            entityName={collection && collection.name}
            loading={deleteInProgress}
          />
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
              }}
            />
          )}
        </div>
      );
    }
  }
);
