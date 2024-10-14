/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useState, useEffect, useCallback} from 'react';
import {withRouter} from 'react-router-dom';
import {OverflowMenu, OverflowMenuItem} from '@carbon/react';
import {Notification, Add} from '@carbon/icons-react';

import {AlertModal, Deleter} from 'components';
import {addNotification, showError} from 'notifications';
import {
  loadAlerts,
  addAlert,
  editAlert,
  removeAlert,
  getCollection,
  isAlertCompatibleReport,
  loadReports,
} from 'services';
import {t} from 'translation';
import {useErrorHandling} from 'hooks';

import './AlertsDropdown.scss';

export function AlertsDropdown({dashboardTiles, numberReport, location}) {
  const [alerts, setAlerts] = useState([]);
  const [reports, setReports] = useState([]);
  const [openAlert, setOpenAlert] = useState();
  const [deleting, setDeleting] = useState();
  const [loading, setLoading] = useState(false);
  const collection = React.useMemo(() => getCollection(location.pathname), [location]);
  const {mightFail} = useErrorHandling();

  const loadEntityAlerts = useCallback(() => {
    mightFail(loadAlerts(collection), setAlerts, showError);
  }, [mightFail, collection]);

  const loadCollectionReports = useCallback(() => {
    if (numberReport) {
      return setReports([numberReport]);
    }
    mightFail(
      loadReports(collection),
      (reports) => setReports(reports.filter((report) => isAlertCompatibleReport(report))),
      showError
    );
  }, [mightFail, numberReport, collection]);

  useEffect(() => {
    if (collection) {
      loadEntityAlerts();
      loadCollectionReports();
    }
  }, [loadEntityAlerts, loadCollectionReports, location, collection, mightFail]);

  const addNewAlert = async (newAlert) => {
    setLoading(true);
    await mightFail(
      addAlert(newAlert),
      () => {
        addNotification({
          type: 'success',
          text: t('common.collection.created', {name: newAlert.name}),
        });
        loadEntityAlerts();
        setOpenAlert();
      },
      showError,
      () => setLoading(false)
    );
  };

  const editExistingAlert = async (changedAlert) => {
    setLoading(true);
    await mightFail(
      editAlert(openAlert.id, changedAlert),
      () => {
        addNotification({
          type: 'success',
          text: t('alert.updated', {name: changedAlert.name}),
        });
        loadEntityAlerts();
        setOpenAlert();
      },
      showError,
      () => setLoading(false)
    );
  };

  const reportsInScope = reports.filter((report) =>
    (dashboardTiles || [numberReport]).some(({id}) => report.id === id)
  );
  const alertsInScope = alerts.filter((alert) =>
    reportsInScope.some(({id}) => id === alert.reportId)
  );

  if (!collection) {
    return null;
  }

  return (
    <div className="AlertsDropdown tool-button">
      <OverflowMenu
        aria-label={t('alert.label-plural')}
        iconDescription={t('alert.label-plural')}
        renderIcon={Notification}
        size="lg"
        flipped
      >
        {alertsInScope.map((alert) => (
          <OverflowMenuItem
            key={alert.id}
            itemText={alert.name}
            onClick={() => setOpenAlert(alert)}
          />
        ))}
        {reportsInScope.length ? (
          <OverflowMenuItem
            itemText={
              <>
                <Add /> {t('alert.createNew')}
              </>
            }
            onClick={() => setOpenAlert({})}
            className="NewAlertDropdownOption"
            hasDivider
          />
        ) : (
          <OverflowMenuItem disabled requireTitle itemText={t('alert.form.reportInfo')} />
        )}
      </OverflowMenu>
      <Deleter
        type="alert"
        entity={deleting}
        onDelete={loadEntityAlerts}
        onClose={() => setDeleting()}
        deleteEntity={async ({id}) => {
          await removeAlert(id);
          addNotification({
            type: 'success',
            text: t('alert.removed', {name: openAlert.name}),
          });
          setOpenAlert();
        }}
      />
      {openAlert && reports && (
        <AlertModal
          initialAlert={openAlert}
          initialReport={numberReport?.id}
          reports={reportsInScope}
          onClose={() => setOpenAlert()}
          onConfirm={(alert) => {
            if (openAlert.id) {
              editExistingAlert(alert);
            } else {
              addNewAlert(alert);
            }
          }}
          onRemove={openAlert.id ? () => setDeleting(openAlert) : undefined}
          disabled={loading}
        />
      )}
    </div>
  );
}

export default withRouter(AlertsDropdown);
