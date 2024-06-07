/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useState} from 'react';

import {loadEntity} from 'services';
import {useErrorHandling} from 'hooks';

export default function useReportDefinitions(existingReport, errorHandler) {
  const [definitions, setDefinitions] = useState();
  const {mightFail} = useErrorHandling();

  useEffect(() => {
    const {id, data} = existingReport || {};

    if (!id && !data?.definitions) {
      return setDefinitions([]);
    }

    if (id) {
      mightFail(
        loadEntity('report', id),
        (report) => setDefinitions(report.data.definitions),
        errorHandler
      );
    } else if (data?.definitions) {
      setDefinitions(data.definitions);
    }
  }, [existingReport, errorHandler, mightFail]);

  return {definitions};
}
