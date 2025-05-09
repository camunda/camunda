/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.utils;

/**
 * Class represents call tree path to store sequence of calls in case of call activities.
 *
 * <p>PI = Process instance, FN = Flow node, FNI = Flow node instance.
 *
 * <p>If we have a process with call activity, then tree path for child process instance will be
 * build as
 * PI_<parent_process_instance_id>/FN_<call_activity_id>/FNI_<parent_flow_node_instance_id_of_call_activity_type>/PI_<child_process_instance_id>,
 * for the incident in child instance we will have tree path as
 * PI_<parent_process_instance_id>/FN_<call_activity_id>/FNI_<parent_flow_node_instance_id_of_call_activity_type>/PI_<child_process_instance_id>/FN_<flow_node_id>/FNI<flow_node_instance_id_where_incident_happenned>.
 */
public class TreePath {

  private StringBuffer treePath;

  public TreePath() {
    treePath = new StringBuffer();
  }

  public TreePath startTreePath(final String processInstanceId) {
    treePath = new StringBuffer(String.format("%s_%s", TreePathEntryType.PI, processInstanceId));
    return this;
  }

  public TreePath startTreePath(final long processInstanceKey) {
    return startTreePath(String.valueOf(processInstanceKey));
  }

  public void appendFlowNode(final String newEntry) {
    if (newEntry != null) {
      treePath.append(String.format("/%s_%s", TreePathEntryType.FN, newEntry));
    }
  }

  public void appendFlowNodeInstance(final String newEntry) {
    if (newEntry != null) {
      treePath.append(String.format("/%s_%s", TreePathEntryType.FNI, newEntry));
    }
  }

  public void appendProcessInstance(final String newEntry) {
    if (newEntry != null) {
      if (treePath == null || treePath.isEmpty()) {
        startTreePath(newEntry);
      } else {
        treePath.append(String.format("/%s_%s", TreePathEntryType.PI, newEntry));
      }
    }
  }

  public void appendProcessInstance(final long newEntry) {
    appendProcessInstance(String.valueOf(newEntry));
  }

  public boolean isEmpty() {
    return treePath == null || treePath.isEmpty();
  }

  @Override
  public String toString() {
    return treePath.toString();
  }

  public enum TreePathEntryType {
    /** Process instance. */
    PI,
    /** Flow node instance. */
    FNI,
    /** Flow node. */
    FN
  }
}
