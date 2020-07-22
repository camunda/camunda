/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Redirect} from 'react-router-dom';
import {parseISO} from 'date-fns';

import {format} from 'dates';
import {withErrorHandling, withUser} from 'HOC';
import {showError} from 'notifications';
import {t} from 'translation';
import {EntityList, Deleter} from 'components';
import {createEntity, updateEntity, checkDeleteConflict} from 'services';

import Copier from './Copier';
import CreateNewButton from './CreateNewButton';
import CollectionModal from './modals/CollectionModal';
import ReportTemplateModal from './modals/ReportTemplateModal';
import {loadEntities} from './service';

import {formatLink, formatType, formatSubEntities, formatUserCount} from './formatters';

import './Home.scss';

export class Home extends React.Component {
  state = {
    entities: null,
    deleting: null,
    copying: null,
    redirect: null,
    creatingCollection: false,
    creatingProcessReport: false,
    editingCollection: null,
  };

  componentDidMount() {
    this.loadList();
  }

  loadList = () => {
    this.props.mightFail(
      loadEntities(),
      (entities) => this.setState({entities}),
      (error) => {
        showError(error);
        this.setState({entities: []});
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

  render() {
    const {
      entities,
      deleting,
      copying,
      creatingCollection,
      creatingProcessReport,
      editingCollection,
      redirect,
    } = this.state;

    if (redirect) {
      return <Redirect to={redirect} />;
    }

    return (
      <div className="Home">
        <div className="welcomeMessage">
          {t('home.welcome')}, {this.props.user?.name}
        </div>
        <div className="content">
          <EntityList
            name={t('home.title')}
            action={
              <CreateNewButton
                createCollection={this.startCreatingCollection}
                createProcessReport={() => this.setState({creatingProcessReport: true})}
              />
            }
            empty={t('home.empty')}
            isLoading={!entities}
            columns={[
              t('common.name'),
              t('home.contents'),
              t('common.entity.modified'),
              t('home.members'),
            ]}
            data={
              entities &&
              entities.map((entity) => {
                const {
                  id,
                  entityType,
                  currentUserRole,
                  lastModified,
                  name,
                  data,
                  reportType,
                  combined,
                } = entity;

                return {
                  link: formatLink(id, entityType),
                  icon: entityType,
                  type: formatType(entityType, reportType, combined),
                  name,
                  meta: [
                    formatSubEntities(data.subEntityCounts),
                    format(parseISO(lastModified), 'yyyy-MM-dd HH:mm'),
                    formatUserCount(data.roleCounts),
                  ],
                  actions: (entityType !== 'collection' || currentUserRole === 'manager') && [
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
                    },
                  ],
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
      </div>
    );
  }
}

export default withErrorHandling(withUser(Home));
