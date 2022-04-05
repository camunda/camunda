/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.util;

import static io.camunda.operate.zeebeimport.util.TreePath.TreePathEntryType.FN;
import static io.camunda.operate.zeebeimport.util.TreePath.TreePathEntryType.FNI;
import static io.camunda.operate.zeebeimport.util.TreePath.TreePathEntryType.PI;

import java.util.Arrays;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Class represents call tree path to store sequence of calls in case of call activities.
 *
 * PI = Process instance, FN = Flow node, FNI = Flow node instance.
 *
 * If we have a process with call activity, then tree path for child process instance will be build
 * as PI_<parent_process_instance_id>/FN_<call_activity_id>/FNI_<parent_flow_node_instance_id_of_call_activity_type>/PI_<child_process_instance_id>,
 * for the incident in child instance we will have tree path as
 * PI_<parent_process_instance_id>/FN_<call_activity_id>/FNI_<parent_flow_node_instance_id_of_call_activity_type>/PI_<child_process_instance_id>/FN_<flow_node_id>/FNI<flow_node_instance_id_where_incident_happenned>.
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

  public TreePath appendFlowNode(String newEntry) {
    if (newEntry != null) {
      treePath.append(String.format("/%s_%s", FN, newEntry));
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

  public TreePath appendEntries(String callActivityId, String flowNodeInstanceId, String processInstanceId) {
    return appendFlowNode(callActivityId).appendFlowNodeInstance(flowNodeInstanceId)
        .appendProcessInstance(processInstanceId);
  }

  public static String extractFlowNodeInstanceId(final String treePath, String currentTreePath) {
    final Pattern fniPattern = Pattern
        .compile(String.format("%s/FN_[^/]*/FNI_(\\d*)/.*", currentTreePath));
    final Matcher matcher = fniPattern.matcher(treePath);
    matcher.matches();
    return matcher.group(1);
  }

  public String extractRootInstanceId() {
    final Pattern piPattern = Pattern.compile("PI_(\\d*).*");
    final Optional<Matcher> firstMatch = Arrays.stream(treePath.toString().split("/"))
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
        .forEach(matcher ->
            processInstanceIds.add(matcher.group(1))

        );
    return processInstanceIds;
  }

  public List<String> extractFlowNodeInstanceIds() {
    final List<String> flowNodeInstanceIds = new ArrayList<>();
    final Pattern fniPattern = Pattern.compile("FNI_(\\d*)$");
    Arrays.stream(treePath.toString().split("/"))
        .map(fniPattern::matcher)
        .filter(Matcher::matches)
        .forEach(matcher ->
            flowNodeInstanceIds.add(matcher.group(1))

        );
    return flowNodeInstanceIds;
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
    FNI,
    /**
     * Flow node.
     */
    FN
  }

}
