/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRef, useState} from 'react';
import {useParams} from 'react-router-dom';
import {CopyFile, DocumentProtected, Edit, Save, TrashCan} from '@carbon/icons-react';
import {Button} from '@carbon/react';
import {parseISO} from 'date-fns';

import {
  BulkDeleter,
  EmptyState,
  EntityList,
  ReportTemplateModal,
  DashboardTemplateModal,
  KpiCreationModal,
} from 'components';
import {OptimizeDashboard} from 'icons';
import {format} from 'dates';
import {t} from 'translation';
import {formatters, getEntityIcon} from 'services';
import {useErrorHandling} from 'hooks';
import {showError} from 'notifications';

import CreateNewButton from './CreateNewButton';
import {checkConflicts, importEntity, removeEntities} from './service';
import {formatLink, formatSubEntities, formatType} from './formatters';

import './CollectionEnitiesList.scss';

export default function CollectionEnitiesList({
  collection,
  entities,
  isLoading,
  sorting,
  copyEntity,
  deleteEntity,
  loadEntities,
  redirectTo,
}) {
  const hasEditRights = collection && collection.currentUserRole !== 'viewer';
  const [creating, setCreating] = useState(null);
  const fileInput = useRef(null);
  const {mightFail} = useErrorHandling();
  const {1: rawId} = useParams();
  const id = rawId.replace(/\/$/, '');

  function closeCreationModal() {
    setCreating(null);
  }

  function openCreationModal(type) {
    setCreating(type);
  }

  function createUploadedEntity() {
    const reader = new FileReader();

    reader.addEventListener('load', () => {
      mightFail(importEntity(reader.result, id), loadEntities, showError);
      if (fileInput.current) {
        fileInput.current.value = null;
      }
    });
    reader.readAsText(fileInput.current?.files[0]);
  }

  function importEntityFromFile() {
    fileInput.current?.click();
  }

  return (
    <div className="CollectionEnitiesList">
      <EntityList
        emptyStateComponent={
          hasEditRights ? (
            <EmptyState
              title={t('home.emptyState.title')}
              description={t('home.emptyState.description')}
              icon={<OptimizeDashboard />}
              actions={
                <>
                  <Button size="md" onClick={() => openCreationModal('dashboard')}>
                    {t('dashboard.createNew')}
                  </Button>
                  <CreateNewButton
                    collection={collection.id}
                    create={(type) => openCreationModal(type)}
                    importEntity={importEntityFromFile}
                  />
                </>
              }
            />
          ) : (
            <EmptyState
              icon={<DocumentProtected />}
              title={t('home.empty')}
              description={t('home.contactManager')}
            />
          )
        }
        action={
          hasEditRights && (
            <CreateNewButton
              kind="primary"
              size="lg"
              collection={collection.id}
              create={(type) => openCreationModal(type)}
              importEntity={importEntityFromFile}
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
        isLoading={isLoading}
        sorting={sorting}
        onChange={loadEntities}
        headers={[
          {name: t('common.name'), key: 'name', defaultOrder: 'asc'},
          t('common.description'),
          t('home.contents'),
          {name: t('common.entity.modifiedBy'), key: 'lastModifier', defaultOrder: 'asc'},
          {
            name: t('common.entity.modified'),
            key: 'lastModified',
            defaultOrder: 'desc',
          },
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
            const actions = [
              {
                icon: <CopyFile />,
                text: t('common.copy'),
                action: () => copyEntity(entity),
              },
            ];
            if (currentUserRole === 'editor') {
              actions.unshift({
                icon: <Edit />,
                text: t('common.edit'),
                action: () => redirectTo(formatLink(id, entityType) + 'edit'),
              });
              actions.push(
                {
                  icon: <TrashCan />,
                  text: t('common.delete'),
                  action: () => deleteEntity(entity),
                },
                {
                  icon: <Save />,
                  text: t('common.export'),
                  action: () => {
                    window.location.href =
                      `api/export/${entityType}/json/${
                        entity.id
                      }/${encodeURIComponent(formatters.formatFileName(entity.name))}.json`;
                  },
                }
              );
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
      />
      {creating === 'report' && <ReportTemplateModal onClose={closeCreationModal} />}
      {creating === 'dashboard' && <DashboardTemplateModal onClose={closeCreationModal} />}
      {creating === 'kpi' && <KpiCreationModal onClose={closeCreationModal} />}
      <input
        className="hidden"
        onChange={createUploadedEntity}
        type="file"
        accept=".json"
        ref={fileInput}
      />
    </div>
  );
}
