/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {withRouter} from 'react-router';

import {t} from 'translation';
import {LoadingIndicator, Modal, ReportTemplateModal} from 'components';
import {useErrorHandling} from 'hooks';
import {addNotification, showError} from 'notifications';
import {createEntity, getCollection} from 'services';

import useReportDefinitions from '../useReportDefinitions';

export function ReportCreationModal({onClose, existingReport, onConfirm, location}) {
  const {definitions} = useReportDefinitions(existingReport, showError);
  const {mightFail} = useErrorHandling();

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

  if (!definitions) {
    return (
      <Modal open>
        <Modal.Footer>
          <LoadingIndicator />
        </Modal.Footer>
      </Modal>
    );
  }

  return (
    <ReportTemplateModal
      onClose={onClose}
      onConfirm={createReport}
      initialDefinitions={definitions}
    />
  );
}

export default withRouter(ReportCreationModal);
