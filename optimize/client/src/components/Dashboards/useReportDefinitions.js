/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
