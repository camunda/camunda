/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.operate;

import static io.camunda.webapps.operate.TreePath.TreePathEntryType.FN;
import static io.camunda.webapps.operate.TreePath.TreePathEntryType.FNI;
import static io.camunda.webapps.operate.TreePath.TreePathEntryType.PI;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

  public TreePath(final String treePath) {
    this.treePath = new StringBuffer(treePath);
  }

  public static String extractFlowNodeInstanceId(
      final String treePath, final String currentTreePath) {
    final Pattern fniPattern =
        Pattern.compile(String.format("%s/FN_[^/]*/FNI_(\\d*)/.*", currentTreePath));
    final Matcher matcher = fniPattern.matcher(treePath);
    matcher.matches();
    return matcher.group(1);
  }

  public TreePath startTreePath(final String processInstanceId) {
    treePath = new StringBuffer(String.format("%s_%s", PI, processInstanceId));
    return this;
  }

  public TreePath startTreePath(final long processInstanceKey) {
    return startTreePath(String.valueOf(processInstanceKey));
  }

  public TreePath appendFlowNode(final String newEntry) {
    if (newEntry != null) {
      treePath.append(String.format("/%s_%s", FN, newEntry));
    }
    return this;
  }

  public TreePath appendFlowNode(final int newEntry) {
    return appendFlowNode(String.valueOf(newEntry));
  }

  public TreePath appendFlowNodeInstance(final String newEntry) {
    if (newEntry != null) {
      treePath.append(String.format("/%s_%s", FNI, newEntry));
    }
    return this;
  }

  public TreePath appendFlowNodeInstance(final long newEntry) {
    return appendFlowNodeInstance(String.valueOf(newEntry));
  }

  public TreePath appendProcessInstance(final String newEntry) {
    if (newEntry != null) {
      if (treePath == null || treePath.isEmpty()) {
        startTreePath(newEntry);
      } else {
        treePath.append(String.format("/%s_%s", PI, newEntry));
      }
    }
    return this;
  }

  public TreePath appendProcessInstance(final long newEntry) {
    return appendProcessInstance(String.valueOf(newEntry));
  }

  public boolean isEmpty() {
    return treePath == null || treePath.isEmpty();
  }

  public TreePath appendEntries(
      final String callActivityId,
      final String flowNodeInstanceId,
      final String processInstanceId) {
    return appendFlowNode(callActivityId)
        .appendFlowNodeInstance(flowNodeInstanceId)
        .appendProcessInstance(processInstanceId);
  }

  public String extractRootInstanceId() {
    final Pattern piPattern = Pattern.compile("PI_(\\d*).*");
    final Optional<Matcher> firstMatch =
        Arrays.stream(treePath.toString().split("/"))
            .map(piPattern::matcher)
            .filter(Matcher::matches)
            .findFirst();
    if (firstMatch.isPresent()) {
      return firstMatch.get().group(1);
    } else {
      return null;
    }
  }

  public List<String> extractProcessInstanceIds() {
    final List<String> processInstanceIds = new ArrayList<>();
    final Pattern piPattern = Pattern.compile("PI_(\\d*)$");
    Arrays.stream(treePath.toString().split("/"))
        .map(piPattern::matcher)
        .filter(Matcher::matches)
        .forEach(matcher -> processInstanceIds.add(matcher.group(1)));

    return processInstanceIds;
  }

  public List<String> extractFlowNodeInstanceIds() {
    final List<String> flowNodeInstanceIds = new ArrayList<>();
    final Pattern fniPattern = Pattern.compile("FNI_(\\d*)$");
    Arrays.stream(treePath.toString().split("/"))
        .map(fniPattern::matcher)
        .filter(Matcher::matches)
        .forEach(matcher -> flowNodeInstanceIds.add(matcher.group(1)));

    return flowNodeInstanceIds;
  }

  @Override
  public String toString() {
    return treePath.toString();
  }

  public TreePath removeProcessInstance(final String processInstanceKey) {
    final List<String> treePathEntries = new ArrayList<>();
    Collections.addAll(treePathEntries, treePath.toString().split("/"));
    final int piIndex = treePathEntries.indexOf("PI_" + processInstanceKey);
    if (piIndex > -1) {
      if (piIndex == treePathEntries.size() - 1) {
        // if last element remove only that one
        treePathEntries.remove(piIndex);
      } else {
        // else remove 3 elements: PI, FN and FNI
        treePathEntries.remove(piIndex + 2);
        treePathEntries.remove(piIndex + 1);
        treePathEntries.remove(piIndex);
      }
      treePath = new StringBuffer(String.join("/", treePathEntries));
      return this;
    }
    return this;
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
