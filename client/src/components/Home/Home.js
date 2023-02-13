/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useCallback, useEffect, useRef, useState} from 'react';
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
  EmptyState,
  Button,
  LoadingIndicator,
} from 'components';
import {formatters, createEntity, updateEntity, checkDeleteConflict} from 'services';

import Copier from './Copier';
import CreateNewButton from './CreateNewButton';
import CollectionModal from './modals/CollectionModal';
import {loadEntities, importEntity, removeEntities, checkConflicts} from './service';

import {formatLink, formatType, formatSubEntities} from './formatters';

import './Home.scss';

export function Home({mightFail, user}) {
  const [entities, setEntities] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const [copying, setCopying] = useState(null);
  const [redirect, setRedirect] = useState(null);
  const [creatingCollection, setCreatingCollection] = useState(false);
  const [creatingProcessReport, setCreatingProcessReport] = useState(false);
  const [creatingDashboard, setCreatingDashboard] = useState(false);
  const [editingCollection, setEditingCollection] = useState(null);
  const [sorting, setSorting] = useState(null);
  const [isLoading, setIsLoading] = useState(true);

  const fileInput = useRef();

  const loadList = useCallback(
    (sortBy, sortOrder) => {
      setIsLoading(true);
      setSorting({key: sortBy, order: sortOrder});
      mightFail(
        loadEntities(sortBy, sortOrder),
        (entities) => {
          setEntities(entities);
          setIsLoading(false);
        },
        (error) => {
          showError(error);
          setEntities([]);
          setIsLoading(false);
        }
      );
    },
    [mightFail]
  );

  useEffect(() => {
    loadList();
  }, [loadList]);

  const startCreatingCollection = () => setCreatingCollection(true);
  const stopCreatingCollection = () => setCreatingCollection(false);

  const startEditingCollection = (editingCollection) => {
    setEditingCollection(editingCollection);
  };
  const stopEditingCollection = () => {
    setEditingCollection(null);
  };

  const edit = (entity) => {
    const {entityType, id} = entity;
    if (entityType === 'collection') {
      startEditingCollection(entity);
    } else {
      setRedirect(formatLink(id, entityType) + 'edit');
    }
  };

  const createUploadedEntity = () => {
    const reader = new FileReader();

    reader.addEventListener('load', () => {
      mightFail(importEntity(reader.result), loadList, showError);
      fileInput.current.value = null;
    });
    reader.readAsText(fileInput.current.files[0]);
  };

  if (redirect) {
    return <Redirect to={redirect} />;
  }

  const isEditor = user?.authorizations.includes('entity_editor');

  const showEmptyStateComponent = !entities?.length && isEditor;

  return (
    <div className="Home">
      <div className="welcomeMessage">
        {t('home.welcome')}, {user?.name}
      </div>
      <div className="content">
        {isLoading && <LoadingIndicator />}
        {!isLoading && showEmptyStateComponent && (
          <EmptyState
            title={t('home.emptyState.title')}
            description={t('home.emptyState.description')}
            icon="dashboard-optimize-accent"
            actions={
              <>
                <Button main primary onClick={() => setCreatingDashboard(true)}>
                  {t('dashboard.createNew')}
                </Button>
                <CreateNewButton
                  createCollection={startCreatingCollection}
                  createProcessReport={() => setCreatingProcessReport(true)}
                  createDashboard={() => setCreatingDashboard(true)}
                  importEntity={() => fileInput.current.click()}
                />
              </>
            }
          />
        )}
        {!isLoading && !showEmptyStateComponent && (
          <EntityList
            name={t('home.title')}
            action={(bulkActive) =>
              isEditor && (
                <CreateNewButton
                  primary={!bulkActive}
                  createCollection={startCreatingCollection}
                  createProcessReport={() => setCreatingProcessReport(true)}
                  createDashboard={() => setCreatingDashboard(true)}
                  importEntity={() => fileInput.current.click()}
                />
              )
            }
            bulkActions={
              isEditor && [
                <BulkDeleter
                  type="delete"
                  deleteEntities={removeEntities}
                  checkConflicts={checkConflicts}
                  conflictMessage={t('common.deleter.affectedMessage.bulk.report')}
                />,
              ]
            }
            empty={t('home.empty')}
            sorting={sorting}
            onChange={loadList}
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
                if (
                  currentUserRole === 'manager' ||
                  (currentUserRole === 'editor' && entityType !== 'collection')
                ) {
                  actions.push(
                    {
                      icon: 'edit',
                      text: t('common.edit'),
                      action: () => edit(entity),
                    },
                    {
                      icon: 'copy-document',
                      text: t('common.copy'),
                      action: () => setCopying(entity),
                    },
                    {
                      icon: 'delete',
                      text: t('common.delete'),
                      action: () => setDeleting(entity),
                    }
                  );
                }

                if (currentUserRole === 'editor' && entityType !== 'collection') {
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
        )}
      </div>
      <Deleter
        entity={deleting}
        type={deleting && deleting.entityType}
        onDelete={loadList}
        checkConflicts={async () => {
          const {entityType, id} = deleting;
          if (entityType === 'report') {
            return checkDeleteConflict(id, entityType);
          }
          return {conflictedItems: []};
        }}
        onClose={() => setDeleting(null)}
      />
      <Copier
        entity={copying}
        onCopy={(name, redirect) => {
          setCopying(null);
          if (!redirect) {
            loadList();
          }
        }}
        onCancel={() => setCopying(null)}
      />
      {creatingCollection && (
        <CollectionModal
          title={t('common.collection.modal.title.new')}
          initialName={t('common.collection.modal.defaultName')}
          confirmText={t('common.collection.modal.createBtn')}
          onClose={stopCreatingCollection}
          onConfirm={(name) => createEntity('collection', {name})}
          showSourcesModal
        />
      )}
      {editingCollection && (
        <CollectionModal
          title={t('common.collection.modal.title.edit')}
          initialName={editingCollection.name}
          confirmText={t('common.collection.modal.editBtn')}
          onClose={stopEditingCollection}
          onConfirm={async (name) => {
            await updateEntity('collection', editingCollection.id, {name});
            stopEditingCollection();
            loadList();
          }}
        />
      )}
      {creatingProcessReport && (
        <ReportTemplateModal onClose={() => setCreatingProcessReport(false)} />
      )}
      {creatingDashboard && <DashboardTemplateModal onClose={() => setCreatingDashboard(false)} />}
      <input
        className="hiddenFilterInput"
        onChange={createUploadedEntity}
        type="file"
        accept=".json"
        ref={fileInput}
      />
    </div>
  );
}

export default withErrorHandling(withUser(Home));
