/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Link, Redirect} from 'react-router-dom';
import classnames from 'classnames';
import {parseISO} from 'date-fns';

import {format} from 'dates';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {
  Icon,
  Dropdown,
  EntityList,
  Deleter,
  BulkDeleter,
  Tooltip,
  ReportTemplateModal,
  DashboardTemplateModal,
  Badge,
  PageTitle,
} from 'components';
import {formatters, loadEntity, updateEntity, checkDeleteConflict} from 'services';
import {showError, addNotification} from 'notifications';
import {getOptimizeProfile} from 'config';

import {loadCollectionEntities, importEntity, removeEntities, checkConflicts} from './service';
import {refreshBreadcrumbs} from 'components/navigation';
import Copier from './Copier';
import CreateNewButton from './CreateNewButton';

import UserList from './UserList';
import AlertList from './AlertList';
import SourcesList from './SourcesList';
import CollectionModal from './modals/CollectionModal';

import {formatLink, formatType, formatSubEntities, formatRole} from './formatters';

import './Collection.scss';

export class Collection extends React.Component {
  state = {
    collection: null,
    editingCollection: false,
    creatingProcessReport: false,
    creatingDashboard: false,
    deleting: false,
    redirect: '',
    copying: null,
    entities: null,
    sorting: null,
    isLoading: true,
    optimizeProfile: null,
  };

  fileInput = React.createRef();

  async componentDidMount() {
    this.loadCollection();
    this.setState({optimizeProfile: await getOptimizeProfile()});
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
      (entities) => this.setState({entities, isLoading: false}),
      (error) => {
        showError(error);
        this.setState({entities: null, isLoading: false});
      }
    );
  };

  startEditingCollection = () => {
    this.setState({editingCollection: true});
  };
  stopEditingCollection = () => {
    this.setState({editingCollection: false});
  };

  createUploadedEntity = () => {
    const reader = new FileReader();

    reader.addEventListener('load', () => {
      this.props.mightFail(
        importEntity(reader.result, this.props.match.params.id),
        this.loadEntities,
        showError
      );
      this.fileInput.current.value = null;
    });
    reader.readAsText(this.fileInput.current.files[0]);
  };

  render() {
    const {
      collection,
      deleting,
      editingCollection,
      creatingProcessReport,
      creatingDashboard,
      redirect,
      copying,
      entities,
      sorting,
      isLoading,
      optimizeProfile,
    } = this.state;

    const {match} = this.props;

    const homeTab = match.params.viewMode === undefined;
    const userTab = match.params.viewMode === 'users';
    const alertTab = match.params.viewMode === 'alerts';
    const sourcesTab = match.params.viewMode === 'sources';

    if (redirect) {
      return <Redirect to={redirect} />;
    }

    const collectionEntity = {...collection, entityType: 'collection'};
    const hasEditRights = collection && collection.currentUserRole !== 'viewer';

    return (
      <div className="Collection">
        <PageTitle pageName={t('common.collection.label')} resourceName={collection?.name} />
        <div className="header">
          <Icon size="24" type="collection" />
          {collection && (
            <>
              <Tooltip content={collection.name} position="bottom" overflowOnly>
                <span className="text">{collection.name}</span>
              </Tooltip>
              {collection.currentUserRole === 'manager' && (
                <Dropdown icon label={<Icon type="context-menu" size="24px" />}>
                  <Dropdown.Option onClick={this.startEditingCollection}>
                    <Icon type="edit" />
                    {t('common.edit')}
                  </Dropdown.Option>
                  <Dropdown.Option onClick={() => this.setState({copying: collectionEntity})}>
                    <Icon type="copy-document" />
                    {t('common.copy')}
                  </Dropdown.Option>
                  <Dropdown.Option onClick={() => this.setState({deleting: collectionEntity})}>
                    <Icon type="delete" />
                    {t('common.delete')}
                  </Dropdown.Option>
                </Dropdown>
              )}
              <Badge className="role">{formatRole(collection.currentUserRole)}</Badge>
            </>
          )}
          <ul className="navigation">
            <li className={classnames({active: homeTab})}>
              <Link to=".">{t('home.collectionTitleWithAmpersand')}</Link>
            </li>
            {(optimizeProfile === 'cloud' || optimizeProfile === 'platform') && (
              <>
                <li className={classnames({active: alertTab})}>
                  <Link to="alerts">{t('alert.label-plural')}</Link>
                </li>
                <li className={classnames({active: userTab})}>
                  <Link to="users">{t('common.user.label-plural')}</Link>
                </li>
              </>
            )}
            <li className={classnames({active: sourcesTab})}>
              <Link to="sources">{t('home.sources.title')}</Link>
            </li>
          </ul>
        </div>
        <div className="content">
          {homeTab && (
            <EntityList
              name={t('home.collectionTitle')}
              action={(bulkActive) =>
                hasEditRights && (
                  <CreateNewButton
                    primary={!bulkActive}
                    collection={collection.id}
                    createProcessReport={() => this.setState({creatingProcessReport: true})}
                    createDashboard={() => this.setState({creatingDashboard: true})}
                    importEntity={() => this.fileInput.current.click()}
                  />
                )
              }
              bulkActions={
                hasEditRights && [
                  <BulkDeleter
                    type="delete"
                    deleteEntities={async (selected) => await removeEntities(selected, collection)}
                    checkConflicts={async (selected) => await checkConflicts(selected, collection)}
                    conflictMessage={t('common.deleter.affectedMessage.bulk.report')}
                  />,
                ]
              }
              empty={
                <>
                  {t('home.empty')}
                  {!hasEditRights && (
                    <>
                      <br />
                      {t('home.contactManager')}
                    </>
                  )}
                </>
              }
              isLoading={isLoading}
              sorting={sorting}
              onChange={this.loadEntities}
              columns={[
                {name: 'Type', key: 'entityType', defaultOrder: 'asc', hidden: true},
                {name: t('common.name'), key: 'name', defaultOrder: 'asc'},
                t('home.contents'),
                {name: 'Modified by', key: 'lastModifier', defaultOrder: 'asc'},
                {name: t('common.entity.modified'), key: 'lastModified', defaultOrder: 'desc'},
              ]}
              data={
                entities &&
                entities.map((entity) => {
                  const {
                    id,
                    entityType,
                    currentUserRole,
                    lastModified,
                    lastModifier,
                    name,
                    data,
                    reportType,
                    combined,
                  } = entity;

                  const actions = [
                    {
                      icon: 'copy-document',
                      text: t('common.copy'),
                      action: () => this.setState({copying: entity}),
                    },
                  ];

                  if (currentUserRole === 'editor') {
                    actions.unshift({
                      icon: 'edit',
                      text: t('common.edit'),
                      action: () => this.setState({redirect: formatLink(id, entityType) + 'edit'}),
                    });

                    actions.push(
                      {
                        icon: 'delete',
                        text: t('common.delete'),
                        action: () => this.setState({deleting: entity}),
                      },
                      {
                        icon: 'save',
                        text: t('common.export'),
                        action: () => {
                          window.location.href = `api/export/${entityType}/json/${
                            entity.id
                          }/${encodeURIComponent(formatters.formatFileName(entity.name))}.json`;
                        },
                      }
                    );
                  }

                  return {
                    id,
                    entityType,
                    className: entityType,
                    link: formatLink(id, entityType),
                    icon: entityType,
                    type: formatType(entityType, reportType, combined),
                    name,
                    meta: [
                      formatSubEntities(data.subEntityCounts),
                      lastModifier,
                      format(parseISO(lastModified), 'PP'),
                    ],
                    actions,
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
              onChange={this.loadEntities}
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
            onConfirm={async (name) => {
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
        {creatingProcessReport && (
          <ReportTemplateModal onClose={() => this.setState({creatingProcessReport: false})} />
        )}
        {creatingDashboard && (
          <DashboardTemplateModal onClose={() => this.setState({creatingDashboard: false})} />
        )}
        <input
          className="hidden"
          onChange={this.createUploadedEntity}
          type="file"
          accept=".json"
          ref={this.fileInput}
        />
      </div>
    );
  }
}

export default withErrorHandling(Collection);
