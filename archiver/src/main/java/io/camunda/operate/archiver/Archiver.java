/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.archiver;

import jakarta.annotation.PostConstruct;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface Archiver {
    @PostConstruct
    void startArchiving();

    CompletableFuture<Void> moveDocuments(String sourceIndexName, String idFieldName, String finishDate,
                                          List<Object> ids);

    String getDestinationIndexName(String sourceIndexName, String finishDate);
}
