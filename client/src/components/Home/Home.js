/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Redirect} from 'react-router-dom';
import {parseISO} from 'date-fns';

import {format} from 'dates';
import {withErrorHandling, withUser} from 'HOC';
import {showError} from 'notifications';
import {t} from 'translation';
import {
  EntityList,
  Deleter,
  BulkDeleter,
  ReportTemplateModal,
  DashboardTemplateModal,
} from 'components';
import {formatters, createEntity, updateEntity, checkDeleteConflict} from 'services';

import Copier from './Copier';
import CreateNewButton from './CreateNewButton';
import CollectionModal from './modals/CollectionModal';
import {loadEntities, importEntity, removeEntities, checkConflicts} from './service';

import {formatLink, formatType, formatSubEntities} from './formatters';

import './Home.scss';

export class Home extends React.Component {
  state = {
    entities: null,
    deleting: null,
    copying: null,
    redirect: null,
    creatingCollection: false,
    creatingProcessReport: false,
    creatingDashboard: false,
    editingCollection: null,
    sorting: null,
    isLoading: true,
  };

  fileInput = React.createRef();

  componentDidMount() {
    this.loadList();
  }

  loadList = (sortBy, sortOrder) => {
    this.setState({isLoading: true, sorting: {key: sortBy, order: sortOrder}});
    this.props.mightFail(
      loadEntities(sortBy, sortOrder),
      (entities) => this.setState({entities, isLoading: false}),
      (error) => {
        showError(error);
        this.setState({entities: [], isLoading: false});
      }
    );
  };

  startCreatingCollection = () => this.setState({creatingCollection: true});
  stopCreatingCollection = () => this.setState({creatingCollection: false});

  startEditingCollection = (editingCollection) => {
    this.setState({editingCollection});
  };
  stopEditingCollection = () => {
    this.setState({editingCollection: null});
  };

  edit = (entity) => {
    const {entityType, id} = entity;
    if (entityType === 'collection') {
      this.startEditingCollection(entity);
    } else {
      this.setState({redirect: formatLink(id, entityType) + 'edit'});
    }
  };

  createUploadedEntity = () => {
    const reader = new FileReader();

    reader.addEventListener('load', () => {
      this.props.mightFail(importEntity(reader.result), this.loadList, showError);
      this.fileInput.current.value = null;
    });
    reader.readAsText(this.fileInput.current.files[0]);
  };

  render() {
    const {
      entities,
      deleting,
      copying,
      creatingCollection,
      creatingProcessReport,
      creatingDashboard,
      editingCollection,
      redirect,
      sorting,
      isLoading,
    } = this.state;

    const {user} = this.props;

    if (redirect) {
      return <Redirect to={redirect} />;
    }

    return (
      <div className="Home">
        <div className="welcomeMessage">
          {t('home.welcome')}, {user?.name}
        </div>
        <div className="content">
          <EntityList
            name={t('home.title')}
            action={(bulkActive) => (
              <CreateNewButton
                primary={!bulkActive}
                createCollection={this.startCreatingCollection}
                createProcessReport={() => this.setState({creatingProcessReport: true})}
                createDashboard={() => this.setState({creatingDashboard: true})}
                importEntity={() => this.fileInput.current.click()}
              />
            )}
            bulkActions={[
              <BulkDeleter
                type="delete"
                deleteEntities={removeEntities}
                checkConflicts={checkConflicts}
                conflictMessage={t('common.deleter.affectedMessage.bulk.report')}
              />,
            ]}
            empty={t('home.empty')}
            isLoading={isLoading}
            sorting={sorting}
            onChange={this.loadList}
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

                const actions = [];

                if (entityType !== 'collection' || currentUserRole === 'manager') {
                  actions.push(
                    {
                      icon: 'edit',
                      text: t('common.edit'),
                      action: () => this.edit(entity),
                    },
                    {
                      icon: 'copy-document',
                      text: t('common.copy'),
                      action: () => this.setState({copying: entity}),
                    },
                    {
                      icon: 'delete',
                      text: t('common.delete'),
                      action: () => this.setState({deleting: entity}),
                    }
                  );
                }

                if (user?.authorizations.includes('import_export') && entityType !== 'collection') {
                  actions.push({
                    icon: 'save',
                    text: t('common.export'),
                    action: () => {
                      window.location.href = `api/export/${entityType}/json/${
                        entity.id
                      }/${encodeURIComponent(formatters.formatFileName(entity.name))}.json`;
                    },
                  });
                }

                return {
                  id,
                  entityType,
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
        </div>
        <Deleter
          entity={deleting}
          type={deleting && deleting.entityType}
          onDelete={this.loadList}
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
          onCopy={(name, redirect) => {
            this.setState({copying: null});
            if (!redirect) {
              this.loadList();
            }
          }}
          onCancel={() => this.setState({copying: null})}
        />
        {creatingCollection && (
          <CollectionModal
            title={t('common.collection.modal.title.new')}
            initialName={t('common.collection.modal.defaultName')}
            confirmText={t('common.collection.modal.createBtn')}
            onClose={this.stopCreatingCollection}
            onConfirm={(name) => createEntity('collection', {name})}
            showSourcesModal
          />
        )}
        {editingCollection && (
          <CollectionModal
            title={t('common.collection.modal.title.edit')}
            initialName={editingCollection.name}
            confirmText={t('common.collection.modal.editBtn')}
            onClose={this.stopEditingCollection}
            onConfirm={async (name) => {
              await updateEntity('collection', editingCollection.id, {name});
              this.stopEditingCollection();
              this.loadList();
            }}
          />
        )}
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

export default withErrorHandling(withUser(Home));
