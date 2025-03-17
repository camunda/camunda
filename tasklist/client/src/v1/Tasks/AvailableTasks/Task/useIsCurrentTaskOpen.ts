/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {pages} from 'common/routing';
import {useMatch} from 'react-router-dom';

function useIsCurrentTaskOpen(id: string) {
  const taskDetailsMatch = useMatch(pages.taskDetails());
  const taskDetailsProcessMatch = useMatch(pages.taskDetailsProcess());

  return [
    taskDetailsMatch?.params.id,
    taskDetailsProcessMatch?.params.id,
  ].includes(id);
}

export {useIsCurrentTaskOpen};
