/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebeimport.util;

import static io.camunda.operate.zeebeimport.util.TreePath.TreePathEntryType.FNI;
import static io.camunda.operate.zeebeimport.util.TreePath.TreePathEntryType.PI;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Class represents call tree path to store sequence of calls in case of call activities.
 *
 * PI = Process instance, FNI = Flow node instance.
 *
 * If we have a process with call activity, then tree path for child process instance will be build
 * as PI_<parent_process_instance_id>/FNI_<parent_flow_node_instance_id_of_call_activity_type>/PI_<child_process_instance_id>,
 * for the incident in child instance we will have tree path as
 * PI_<parent_process_instance_id>/FNI_<parent_flow_node_instance_id_of_call_activity_type>/PI_<child_process_instance_id>/FNI<flow_node_instance_id_where_incident_happenned>.
 *
 */
public class TreePath {

  private StringBuffer treePath;

  public TreePath() {
    this.treePath = new StringBuffer();
  }

  public TreePath(final String treePath) {
    this.treePath = new StringBuffer(treePath);
  }

  public TreePath startTreePath(String processInstanceId) {
    treePath = new StringBuffer(String.format("%s_%s", PI, processInstanceId));
    return this;
  }

  private TreePath appendEntry(String newEntry, TreePathEntryType type) {
    if (newEntry != null) {
      treePath.append(String.format("/%s_%s", type, newEntry));
    }
    return this;
  }

  public TreePath appendFlowNodeInstance(String newEntry) {
    if (newEntry != null) {
      treePath.append(String.format("/%s_%s", FNI, newEntry));
    }
    return this;
  }

  public TreePath appendProcessInstance(String newEntry) {
    if (newEntry != null) {
      treePath.append(String.format("/%s_%s", PI, newEntry));
    }
    return this;
  }

  public TreePath appendEntries(String flowNodeInstanceId, String processInstanceId) {
    return appendFlowNodeInstance(flowNodeInstanceId).appendProcessInstance(processInstanceId);
  }

  public static String extractCallActivityId(final String treePath, String currentTreePath) {
    final Pattern fniPattern = Pattern
        .compile(String.format("%s/FNI_(\\d*)/.*", currentTreePath));
    final Matcher matcher = fniPattern.matcher(treePath);
    matcher.matches();
    return matcher.group(1);
  }

  @Override
  public String toString() {
    return treePath.toString();
  }

  public enum TreePathEntryType {
    /**
     * Process instance.
     */
    PI,
    /**
     * Flow node instance.
     */
    FNI
  }

}
