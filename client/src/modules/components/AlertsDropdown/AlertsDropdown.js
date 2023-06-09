/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState, useEffect, useCallback} from 'react';
import {withRouter} from 'react-router-dom';
import classnames from 'classnames';

import {AlertModal, Deleter, Dropdown, Icon, Tooltip} from 'components';
import {withErrorHandling} from 'HOC';
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
import {getWebhooks} from 'config';

import './AlertsDropdown.scss';

export function AlertsDropdown({mightFail, dashboardReports, numberReport, location}) {
  const [alerts, setAlerts] = useState([]);
  const [reports, setReports] = useState([]);
  const [openAlert, setOpenAlert] = useState();
  const [deleting, setDeleting] = useState();
  const [webhooks, setWebhooks] = useState();
  const [loading, setLoading] = useState(false);
  const collection = React.useMemo(() => getCollection(location.pathname), [location]);

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
      mightFail(getWebhooks(), setWebhooks, showError);
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
    (dashboardReports || [numberReport]).some(({id}) => report.id === id)
  );
  const alertsInScope = alerts.filter((alert) =>
    reportsInScope.some(({id}) => id === alert.reportId)
  );

  if (!collection) {
    return null;
  }

  return (
    <div className="AlertsDropdown tool-button">
      <Tooltip content={!reportsInScope.length && t('alert.form.reportInfo')} position="bottom">
        <div>
          <Dropdown
            main
            label={
              <>
                <Icon type="alert" /> {t('alert.label-plural')}
              </>
            }
            disabled={!reportsInScope.length}
          >
            <Dropdown.Option
              className={classnames('createNew', {bottomBorder: alertsInScope.length > 0})}
              onClick={() => setOpenAlert({})}
            >
              {t('alert.newAlert')}
            </Dropdown.Option>
            {alertsInScope.length > 0 && (
              <div className="subTitle">{t('alert.existingAlerts')}</div>
            )}
            {alertsInScope.map((alert) => (
              <Dropdown.Option key={alert.id} onClick={() => setOpenAlert(alert)}>
                {alert.name}
              </Dropdown.Option>
            ))}
          </Dropdown>
        </div>
      </Tooltip>
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
          webhooks={webhooks}
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

export default withRouter(withErrorHandling(AlertsDropdown));
