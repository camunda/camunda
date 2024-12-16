/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Component, forwardRef} from 'react';
import {Column, Grid, Stack} from '@carbon/react';
import {Link} from 'react-router-dom';
import {Folder} from '@carbon/icons-react';
import {C3Page} from '@camunda/camunda-composite-components';

import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {Deleter, PageTitle, Tabs} from 'components';
import {loadEntity, updateEntity, checkDeleteConflict} from 'services';
import {showError, addNotification} from 'notifications';
import {isUserSearchAvailable} from 'config';

import {loadCollectionEntities} from './service';
import {formatRole} from './formatters';
import UserList from './UserList';
import AlertList from './AlertList';
import SourcesList from './SourcesList';
import CollectionModal from './modals/CollectionModal';
import CollectionEnitiesList from './CollectionEnitiesList';
import Copier from './Copier';

import './Collection.scss';

export class Collection extends Component {
  state = {
    collection: null,
    editingCollection: false,
    creating: null,
    deleting: false,
    redirect: '',
    copying: null,
    entities: null,
    sorting: null,
    isLoading: true,
    userSearchAvailable: false,
  };

  async componentDidMount() {
    this.loadCollection();
    this.setState({
      userSearchAvailable: await isUserSearchAvailable(),
    });
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
      (collection) => this.setState({collection}),
      (error) => {
        showError(error);
        this.setState({collection: null});
      }
    );
    this.loadEntities();
  };

  loadEntities = (sortBy, sortOrder) => {
    this.setState({isLoading: true, sorting: {key: sortBy, order: sortOrder}});
    this.props.mightFail(
      loadCollectionEntities(this.props.match.params.id, sortBy, sortOrder),
      (entities) => this.setState({entities}),
      (error) => {
        showError(error);
        this.setState({entities: null});
      },
      () => this.setState({isLoading: false})
    );
  };

  startCollectionEditing = () => {
    this.setState({editingCollection: true});
  };

  finishCollectionEditing = () => {
    this.setState({editingCollection: false});
  };

  copyEntity = (entity) => {
    this.setState({copying: entity});
  };

  deleteEntity = (entity) => {
    this.setState({deleting: entity});
  };

  render() {
    const {
      collection,
      deleting,
      editingCollection,
      redirect,
      copying,
      entities,
      sorting,
      isLoading,
      userSearchAvailable,
    } = this.state;

    const {match} = this.props;

    const currentTab = match.params.viewMode || 'home';

    if (redirect) {
      return this.props.history.push(redirect);
    }

    return (
      <Grid className="Collection" fullWidth>
        <PageTitle pageName={t('common.collection.label')} resourceName={collection?.name} />
        <Column sm={4} md={8} lg={16}>
          <C3Page
            isLoading={isLoading}
            breadcrumbs={{
              elements: [
                {
                  key: '1',
                  label: t('navigation.collections'),
                  routeProps: {
                    to: '/collections',
                  },
                },
              ],
              forwardRef: forwardRef((props, ref) => <Link {...props} ref={ref} />),
            }}
            header={{
              title: (
                <Stack gap={4} orientation="horizontal">
                  <Folder size="24" /> {collection?.name}
                </Stack>
              ),
              tag: collection && formatRole(collection?.currentUserRole),
              menuItems: [
                {
                  key: 'edit',
                  label: t('common.edit'),
                  onClick: () => {
                    this.startCollectionEditing();
                  },
                },
                {
                  key: 'copy',
                  label: t('common.copy'),
                  onClick: () => {
                    this.copyEntity({...collection, entityType: 'collection'});
                  },
                },
                {
                  key: 'delete',
                  label: t('common.delete'),
                  onClick: () => {
                    this.deleteEntity({...collection, entityType: 'collection'});
                  },
                  isDelete: true,
                },
              ],
            }}
          >
            <div>
              <Tabs value={currentTab} isLoading={!collection && isLoading}>
                <Tabs.Tab
                  key="home"
                  value="home"
                  title={t('home.collectionTitleWithAmpersand')}
                  onClick={() => this.props.history.push('.')}
                >
                  <CollectionEnitiesList
                    collection={collection}
                    entities={entities}
                    isLoading={isLoading}
                    sorting={sorting}
                    copyEntity={this.copyEntity}
                    deleteEntity={this.deleteEntity}
                    loadEntities={this.loadEntities}
                    redirectTo={(url) => this.setState({redirect: url})}
                  />
                </Tabs.Tab>
                {userSearchAvailable && collection && (
                  <>
                    <Tabs.Tab
                      key="alerts"
                      value="alerts"
                      title={t('alert.label-plural')}
                      onClick={() => this.props.history.push('alerts')}
                    >
                      <AlertList
                        readOnly={collection.currentUserRole === 'viewer'}
                        collection={collection.id}
                      />
                    </Tabs.Tab>
                    <Tabs.Tab
                      key="users"
                      value="users"
                      title={t('common.user.label-plural')}
                      onClick={() => this.props.history.push('users')}
                    >
                      <UserList
                        readOnly={collection.currentUserRole !== 'manager'}
                        onChange={this.loadCollection}
                        collection={collection.id}
                      />
                    </Tabs.Tab>
                  </>
                )}
                {collection && (
                  <Tabs.Tab
                    key="sources"
                    value="sources"
                    title={t('home.sources.title')}
                    onClick={() => this.props.history.push('sources')}
                  >
                    <SourcesList
                      onChange={this.loadEntities}
                      readOnly={collection.currentUserRole !== 'manager'}
                      collection={collection.id}
                    />
                  </Tabs.Tab>
                )}
              </Tabs>
            </div>
          </C3Page>
          {editingCollection && (
            <CollectionModal
              title={t('common.collection.modal.title.edit')}
              initialName={collection.name}
              confirmText={t('common.collection.modal.editBtn')}
              onClose={this.finishCollectionEditing}
              onConfirm={async (name) => {
                await updateEntity('collection', collection.id, {name});
                this.loadCollection();
                this.finishCollectionEditing();
              }}
            />
          )}
        </Column>
        <Deleter
          entity={deleting}
          type={deleting && deleting.entityType}
          onDelete={() => {
            if (deleting.entityType === 'collection') {
              this.setState({redirect: '/'});
            } else {
              this.loadEntities();
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
              this.loadEntities();
            }
            this.setState({copying: null});
          }}
          onCancel={() => this.setState({copying: null})}
        />
      </Grid>
    );
  }
}

export default withErrorHandling(Collection);
