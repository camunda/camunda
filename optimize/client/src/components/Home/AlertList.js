/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useState, useEffect, useCallback} from 'react';
import {Button} from '@carbon/react';
import {CopyFile, Edit, Notification, TrashCan} from '@carbon/icons-react';

import {t} from 'translation';
import {Deleter, BulkDeleter, AlertModal, EntityList, EmptyState} from 'components';
import {showError} from 'notifications';
import {
  loadAlerts,
  addAlert,
  editAlert,
  removeAlert,
  formatters,
  loadReports,
  isDurationReport,
  isAlertCompatibleReport,
} from 'services';
import {useErrorHandling} from 'hooks';

import CopyAlertModal from './modals/CopyAlertModal';
import {removeAlerts} from './service';

import './AlertList.scss';

const {duration, frequency} = formatters;

const AlertList = ({collection, readOnly}) => {
  const [deleting, setDeleting] = useState(null);
  const [editing, setEditing] = useState(null);
  const [copying, setCopying] = useState(null);
  const [reports, setReports] = useState(null);
  const [alerts, setAlerts] = useState(null);
  const [loading, setLoading] = useState(false);
  const {mightFail} = useErrorHandling();

  const syncAlerts = useCallback(() => {
    mightFail(loadAlerts(collection), (loadedAlerts) => setAlerts(loadedAlerts), showError);
  }, [collection, mightFail]);

  const syncReports = useCallback(() => {
    mightFail(
      loadReports(collection),
      (loadedReports) => {
        setReports(loadedReports.filter(isAlertCompatibleReport));
      },
      (error) => {
        showError(error);
        setReports(null);
      }
    );
  }, [collection, mightFail]);

  const loadData = useCallback(() => {
    syncReports();
    syncAlerts();
  }, [syncAlerts, syncReports]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const openAddAlertModal = () => setEditing({});
  const openEditAlertModal = (alert) => setEditing(alert);

  const onAdd = async (newAlert) => {
    setLoading(true);
    await mightFail(
      addAlert(newAlert),
      () => {
        closeEditAlertModal();
        syncAlerts();
      },
      showError,
      () => setLoading(false)
    );
  };

  const onEdit = async (changedAlert) => {
    setLoading(true);
    await mightFail(
      editAlert(editing.id, changedAlert),
      () => {
        closeEditAlertModal();
        syncAlerts();
      },
      showError,
      () => setLoading(false)
    );
  };

  const closeEditAlertModal = () => setEditing(null);
  const openCopyAlertModal = (alert) => setCopying(alert);
  const closeCopyAlertModal = () => setCopying(null);

  const formatDescription = (reportId, operator, value) => {
    const report = reports?.find(({id}) => id === reportId);
    const aboveOrBelow = operator === '<' ? t('common.below') : t('common.above');
    const thresholdValue = isDurationReport(report) ? duration(value) : frequency(value);

    return t('alert.description', {
      name: report.name,
      aboveOrBelow,
      thresholdValue,
    });
  };

  const isLoading = alerts === null || reports === null;

  return (
    <div className="AlertList">
      <EntityList
        action={
          !readOnly && (
            <Button className="createAlert" kind="primary" onClick={openAddAlertModal}>
              {t('alert.createNew')}
            </Button>
          )
        }
        emptyStateComponent={
          <EmptyState
            title={t('alert.notCreated')}
            description={!readOnly ? t('alert.emptyStateDecription') : t('alert.contactManager')}
            icon={<Notification />}
            actions={
              !readOnly && (
                <Button
                  className="createAlert"
                  size="md"
                  kind="primary"
                  onClick={openAddAlertModal}
                >
                  {t('alert.createNew')}
                </Button>
              )
            }
          />
        }
        isLoading={isLoading}
        headers={[t('common.name'), t('report.label'), t('common.condition'), t('alert.recipient')]}
        bulkActions={!readOnly && [<BulkDeleter type="delete" deleteEntities={removeAlerts} />]}
        onChange={syncAlerts}
        rows={
          !isLoading &&
          alerts.map((alert) => {
            const {id, name, emails, reportId, threshold, thresholdOperator} = alert;

            return {
              id,
              entityType: 'alert',
              icon: <Notification />,
              type: t('alert.label'),
              name,
              meta: [
                reports.find((report) => report.id === reportId).name,
                formatDescription(reportId, thresholdOperator, threshold),
                emails?.join(', '),
              ],
              actions: !readOnly && [
                {
                  icon: <Edit />,
                  text: t('common.edit'),
                  action: () => openEditAlertModal(alert),
                },
                {
                  icon: <CopyFile />,
                  text: t('common.copy'),
                  action: () => openCopyAlertModal(alert),
                },
                {
                  icon: <TrashCan />,
                  text: t('common.delete'),
                  action: () => setDeleting(alert),
                },
              ],
            };
          })
        }
      />
      <Deleter
        type="alert"
        entity={deleting}
        onDelete={syncAlerts}
        onClose={() => setDeleting(null)}
        deleteEntity={({id}) => removeAlert(id)}
      />
      {editing && reports && (
        <AlertModal
          initialAlert={editing}
          reports={reports}
          onClose={closeEditAlertModal}
          onConfirm={(alert) => {
            if (editing.id) {
              onEdit(alert);
            } else {
              onAdd(alert);
            }
          }}
          disabled={loading}
        />
      )}
      {copying && (
        <CopyAlertModal
          initialAlertName={copying.name}
          onClose={closeCopyAlertModal}
          onConfirm={(name) => {
            onAdd({...copying, name});
            closeCopyAlertModal();
          }}
        />
      )}
    </div>
  );
};

export default AlertList;
