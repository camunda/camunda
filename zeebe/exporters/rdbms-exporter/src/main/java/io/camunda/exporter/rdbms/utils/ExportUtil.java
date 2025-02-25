/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.utils;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExportUtil {

  private static final Logger LOG = LoggerFactory.getLogger(ExportUtil.class);

  /**
   * Builds the tree path and level of the flow node instance.
   *
   * <pre>
   *   <processInstanceKey>/<flowNodeInstanceKey>/.../<flowNodeInstanceKey>
   * </pre>
   *
   * Upper level flowNodeInstanceKeys are typically subprocesses or multi-instance bodies. This
   * intra tree path shows the position of a flow node instance within a single process instance.
   * The last entry is used, as we are not interested in upper-level process instance scope
   * hierarchies.
   */
  public static String buildTreePath(
      final Long key, final Long processInstanceKey, final List<List<Long>> elementInstancePath) {
    if (elementInstancePath != null && !elementInstancePath.isEmpty()) {
      final List<String> treePathEntries =
          elementInstancePath.getLast().stream().map(String::valueOf).toList();
      return String.join("/", treePathEntries);
    } else {
      LOG.warn(
          "No elementInstancePath is provided for flow node instance id: {}. TreePath will be set to default value.",
          key);
      return processInstanceKey + "/" + key;
    }
  }
}
