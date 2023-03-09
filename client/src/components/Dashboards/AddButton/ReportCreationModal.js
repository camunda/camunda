/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useState} from 'react';
import {withRouter} from 'react-router';

import {t} from 'translation';
import {LoadingIndicator, Modal, ReportTemplateModal} from 'components';
import {withErrorHandling} from 'HOC';
import {addNotification, showError} from 'notifications';
import {createEntity, getCollection, loadEntity} from 'services';

export function ReportCreationModal({onClose, existingReport, mightFail, onConfirm, location}) {
  const [initialTemplateDefinitions, setInitialTemplateDefinitions] = useState();

  useEffect(() => {
    const {id, data} = existingReport || {};

    if (!id && !data) {
      return setInitialTemplateDefinitions([]);
    }

    if (id) {
      mightFail(
        loadEntity('report', id),
        (report) => setInitialTemplateDefinitions(report.data.definitions),
        showError
      );
    } else if (data) {
      setInitialTemplateDefinitions(data.definitions);
    }
  }, [existingReport, mightFail]);

  function createReport(report) {
    const collectionId = getCollection(location.pathname);

    mightFail(
      createEntity('report/process/single', {...report, collectionId}, 'dashboard'),
      (id) => {
        onConfirm({id, type: 'optimize_report'});
        addNotification({
          type: 'success',
          text: t('common.collection.created', {name: report.name}),
        });
      },
      showError
    );
  }

  if (!initialTemplateDefinitions) {
    return (
      <Modal open>
        <Modal.Content>
          <LoadingIndicator />
        </Modal.Content>
      </Modal>
    );
  }

  return (
    <ReportTemplateModal
      onClose={onClose}
      onConfirm={createReport}
      initialDefinitions={initialTemplateDefinitions}
    />
  );
}

export default withRouter(withErrorHandling(ReportCreationModal));
