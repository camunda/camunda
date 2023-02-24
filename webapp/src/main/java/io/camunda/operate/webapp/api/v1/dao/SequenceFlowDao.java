/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import io.camunda.operate.webapp.api.v1.entities.SequenceFlow;

public interface SequenceFlowDao extends
    SearchableDao<SequenceFlow>,
    SortableDao<SequenceFlow>,
    PageableDao<SequenceFlow> {
}

