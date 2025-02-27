/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.util;

import static io.camunda.operate.util.TreePath.TreePathEntryType.FN;
import static io.camunda.operate.util.TreePath.TreePathEntryType.FNI;
import static io.camunda.operate.util.TreePath.TreePathEntryType.PI;

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
    this.treePath = new StringBuffer();
  }

  public TreePath(final String treePath) {
    this.treePath = new StringBuffer(treePath);
  }

  public static String extractFlowNodeInstanceId(final String treePath, String currentTreePath) {
    final Pattern fniPattern =
        Pattern.compile(String.format("%s/FN_[^/]*/FNI_(\\d*)/.*", currentTreePath));
    final Matcher matcher = fniPattern.matcher(treePath);
    matcher.matches();
    return matcher.group(1);
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

  public TreePath appendEntries(
      String callActivityId, String flowNodeInstanceId, String processInstanceId) {
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

  public TreePath removeProcessInstance(String processInstanceKey) {
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

  public Optional<String> processInstanceForFni(final String fniId) {
    final var compiled = treePath.toString();
    final var pathSegments = compiled.split("/");
    final var fniIndex = Arrays.asList(pathSegments).indexOf("FNI_" + fniId);
    for (int index = fniIndex; index >= 0; index--) {
      final var pathSegment = pathSegments[index];
      if (pathSegment.startsWith("PI_")) {
        return Optional.of(pathSegment.replaceAll("PI_", ""));
      }
    }

    return Optional.empty();
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
