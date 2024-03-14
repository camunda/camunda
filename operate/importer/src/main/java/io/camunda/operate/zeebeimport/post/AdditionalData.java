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
package io.camunda.operate.zeebeimport.post;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AdditionalData {

  private Map<String, String> incidentIndices = new ConcurrentHashMap<>();
  private Map<String, List<String>> flowNodeInstanceIndices = new ConcurrentHashMap<>();
  private Map<String, List<String>> flowNodeInstanceInListViewIndices = new ConcurrentHashMap<>();
  private Map<Long, String> processInstanceTreePaths = new ConcurrentHashMap<>();
  private Map<String, String> incidentTreePaths = new ConcurrentHashMap<>();
  private Map<String, String> processInstanceIndices = new ConcurrentHashMap<>();
  private Map<String, Set<String>> piIdsWithIncidentIds =
      new ConcurrentHashMap<>(); // piId <-> active incident ids
  private Map<String, Set<String>> fniIdsWithIncidentIds =
      new ConcurrentHashMap<>(); // flowNodeInstanceId <-> active incident ids

  public Map<String, List<String>> getFlowNodeInstanceIndices() {
    return flowNodeInstanceIndices;
  }

  public AdditionalData setFlowNodeInstanceIndices(
      final Map<String, List<String>> flowNodeInstanceIndices) {
    this.flowNodeInstanceIndices = flowNodeInstanceIndices;
    return this;
  }

  public Map<String, List<String>> getFlowNodeInstanceInListViewIndices() {
    return flowNodeInstanceInListViewIndices;
  }

  public AdditionalData setFlowNodeInstanceInListViewIndices(
      final Map<String, List<String>> flowNodeInstanceInListViewIndices) {
    this.flowNodeInstanceInListViewIndices = flowNodeInstanceInListViewIndices;
    return this;
  }

  public Map<Long, String> getProcessInstanceTreePaths() {
    return processInstanceTreePaths;
  }

  public AdditionalData setProcessInstanceTreePaths(
      final Map<Long, String> processInstanceTreePaths) {
    this.processInstanceTreePaths = processInstanceTreePaths;
    return this;
  }

  public Map<String, String> getProcessInstanceIndices() {
    return processInstanceIndices;
  }

  public AdditionalData setProcessInstanceIndices(
      final Map<String, String> processInstanceIndices) {
    this.processInstanceIndices = processInstanceIndices;
    return this;
  }

  public Map<String, String> getIncidentIndices() {
    return incidentIndices;
  }

  public AdditionalData setIncidentIndices(final Map<String, String> incidentIndices) {
    this.incidentIndices = incidentIndices;
    return this;
  }

  public Map<String, Set<String>> getPiIdsWithIncidentIds() {
    return piIdsWithIncidentIds;
  }

  public AdditionalData setPiIdsWithIncidentIds(
      final Map<String, Set<String>> piIdsWithIncidentIds) {
    this.piIdsWithIncidentIds = piIdsWithIncidentIds;
    return this;
  }

  public void addPiIdsWithIncidentIds(final String piId, final String incidentId) {
    if (piIdsWithIncidentIds.get(piId) == null) {
      piIdsWithIncidentIds.put(piId, new HashSet<>());
    }
    piIdsWithIncidentIds.get(piId).add(incidentId);
  }

  public void deleteIncidentIdByPiId(final String piId, final String incidentId) {
    if (piIdsWithIncidentIds.get(piId) != null) {
      piIdsWithIncidentIds.get(piId).remove(incidentId);
    }
  }

  public Map<String, Set<String>> getFniIdsWithIncidentIds() {
    return fniIdsWithIncidentIds;
  }

  public AdditionalData setFniIdsWithIncidentIds(
      final Map<String, Set<String>> fniIdsWithIncidentIds) {
    this.fniIdsWithIncidentIds = fniIdsWithIncidentIds;
    return this;
  }

  public void addFniIdsWithIncidentIds(final String fniId, final String incidentId) {
    if (fniIdsWithIncidentIds.get(fniId) == null) {
      fniIdsWithIncidentIds.put(fniId, new HashSet<>());
    }
    fniIdsWithIncidentIds.get(fniId).add(incidentId);
  }

  public void deleteIncidentIdByFniId(final String fniId, final String incidentId) {
    if (fniIdsWithIncidentIds.get(fniId) != null) {
      fniIdsWithIncidentIds.get(fniId).remove(incidentId);
    }
  }

  public Map<String, String> getIncidentTreePaths() {
    return incidentTreePaths;
  }

  public AdditionalData setIncidentTreePaths(final Map<String, String> incidentTreePaths) {
    this.incidentTreePaths = incidentTreePaths;
    return this;
  }
}
