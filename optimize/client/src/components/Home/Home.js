/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useCallback, useEffect, useRef, useState} from 'react';
import {useHistory} from 'react-router-dom';
import {parseISO} from 'date-fns';
import {Button, Grid, Column} from '@carbon/react';
import {CopyFile, Edit, Save, TrashCan} from '@carbon/icons-react';

import {format} from 'dates';
import {withErrorHandling, withUser} from 'HOC';
import {showError} from 'notifications';
import {t} from 'translation';
import {
  Deleter,
  BulkDeleter,
  ReportTemplateModal,
  DashboardTemplateModal,
  EmptyState,
  KpiCreationModal,
  EntityList,
} from 'components';
import {OptimizeDashboard} from 'icons';
import {
  formatters,
  createEntity,
  updateEntity,
  checkDeleteConflict,
  loadEntities,
  getEntityIcon,
} from 'services';

import Copier from './Copier';
import CreateNewButton from './CreateNewButton';
import CollectionModal from './modals/CollectionModal';
import {importEntity, removeEntities, checkConflicts} from './service';

import {formatLink, formatType, formatSubEntities} from './formatters';

import './Home.scss';

export function Home({mightFail, user}) {
  const [entities, setEntities] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const [copying, setCopying] = useState(null);
  const [redirect, setRedirect] = useState(null);
  const [creating, setCreating] = useState(null);
  const [editingCollection, setEditingCollection] = useState(null);
  const [sorting, setSorting] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const history = useHistory();

  const fileInput = useRef();

  const loadList = useCallback(
    (sortBy, sortOrder) => {
      setIsLoading(true);
      setSorting({key: sortBy, order: sortOrder});
      mightFail(
        loadEntities(sortBy, sortOrder),
        (entities) => {
          setEntities(entities);
        },
        (error) => {
          showError(error);
          setEntities([]);
        },
        () => setIsLoading(false)
      );
    },
    [mightFail]
  );

  useEffect(() => {
    loadList();
  }, [loadList]);

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
    history.push(redirect);
  }

  const isEditor = user?.authorizations.includes('entity_editor');

  return (
    <Grid condensed className="Home" fullWidth>
      <Column sm={4} md={8} lg={16}>
        <EntityList
          isLoading={isLoading}
          emptyStateComponent={
            isEditor && (
              <EmptyState
                title={t('home.emptyState.title')}
                description={t('home.emptyState.description')}
                icon={<OptimizeDashboard />}
                actions={
                  <>
                    <Button size="md" onClick={() => setCreating('dashboard')}>
                      {t('dashboard.createNew')}
                    </Button>
                    <CreateNewButton
                      create={setCreating}
                      importEntity={() => fileInput.current.click()}
                    />
                  </>
                }
              />
            )
          }
          title={t('home.title')}
          action={
            isEditor && (
              <CreateNewButton
                size="lg"
                kind="primary"
                create={setCreating}
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
          headers={[
            {name: t('common.name'), key: 'name', defaultOrder: 'asc'},
            t('common.description'),
            t('home.contents'),
            {name: t('common.entity.modifiedBy'), key: 'lastModifier', defaultOrder: 'asc'},
            {name: t('common.entity.modified'), key: 'lastModified', defaultOrder: 'desc'},
          ]}
          rows={
            entities &&
            entities.map((entity) => {
              const {
                id,
                entityType,
                currentUserRole,
                lastModified,
                lastModifier,
                name,
                description,
                data,
              } = entity;

              const actions = [];
              if (
                currentUserRole === 'manager' ||
                (currentUserRole === 'editor' && entityType !== 'collection')
              ) {
                actions.push(
                  {
                    icon: <Edit />,
                    text: t('common.edit'),
                    action: () => edit(entity),
                  },
                  {
                    icon: <CopyFile />,
                    text: t('common.copy'),
                    action: () => setCopying(entity),
                  },
                  {
                    icon: <TrashCan />,
                    text: t('common.delete'),
                    action: () => setDeleting(entity),
                  }
                );
              }

              if (currentUserRole === 'editor' && entityType !== 'collection') {
                actions.push({
                  icon: <Save />,
                  text: t('common.export'),
                  action: () => {
                    window.location.href =
                      `api/export/${entityType}/json/${
                        entity.id
                      }/${encodeURIComponent(formatters.formatFileName(entity.name))}.json`;
                  },
                });
              }

              return {
                id,
                entityType,
                link: formatLink(id, entityType),
                icon: getEntityIcon(entityType),
                type: formatType(entityType),
                name,
                meta: [
                  description,
                  formatSubEntities(data.subEntityCounts),
                  lastModifier,
                  format(parseISO(lastModified), 'PP'),
                ],
                actions,
              };
            })
          }
          sorting={sorting}
          onChange={loadList}
        />
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
          onCopy={(_name, redirect) => {
            setCopying(null);
            if (!redirect) {
              loadList();
            }
          }}
          onCancel={() => setCopying(null)}
        />
        {creating === 'collection' && (
          <CollectionModal
            title={t('common.collection.modal.title.new')}
            initialName={t('common.collection.modal.defaultName')}
            confirmText={t('common.collection.modal.createBtn')}
            onClose={() => setCreating(null)}
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
        {creating === 'report' && <ReportTemplateModal onClose={() => setCreating(null)} />}
        {creating === 'kpi' && <KpiCreationModal onClose={() => setCreating(null)} />}
        {creating === 'dashboard' && <DashboardTemplateModal onClose={() => setCreating(null)} />}
        <input
          className="hiddenFilterInput"
          onChange={createUploadedEntity}
          type="file"
          accept=".json"
          ref={fileInput}
        />
      </Column>
    </Grid>
  );
}

export default withErrorHandling(withUser(Home));
