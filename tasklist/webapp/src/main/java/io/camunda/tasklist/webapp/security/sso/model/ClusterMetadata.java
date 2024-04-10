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
package io.camunda.tasklist.webapp.security.sso.model;

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.tasklist.webapp.graphql.entity.C8AppLink;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

public class ClusterMetadata implements Serializable {

  public enum AppName {
    @JsonProperty("console")
    CONSOLE,
    @JsonProperty("operate")
    OPERATE,
    @JsonProperty("optimize")
    OPTIMIZE,
    @JsonProperty("modeler")
    MODELER,
    @JsonProperty("tasklist")
    TASKLIST,
    @JsonProperty("zeebe")
    ZEEBE,
    @JsonProperty("connectors")
    CONNECTORS;

    @Override
    public String toString() {
      return super.toString().toLowerCase();
    }
  }

  private String uuid;
  private String name;
  private Map<AppName, String> urls = new HashMap<>();

  public String getUuid() {
    return uuid;
  }

  public ClusterMetadata setUuid(String uuid) {
    this.uuid = uuid;
    return this;
  }

  public String getName() {
    return name;
  }

  public ClusterMetadata setName(String name) {
    this.name = name;
    return this;
  }

  public Map<AppName, String> getUrls() {
    return urls;
  }

  public List<C8AppLink> getUrlsAsC8AppLinks() {
    return urls.keySet().stream()
        .map(name -> new C8AppLink().setName(name.name()).setLink(urls.get(name)))
        .collect(Collectors.toList());
  }

  public ClusterMetadata setUrls(Map<AppName, String> urls) {
    this.urls = urls;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ClusterMetadata that = (ClusterMetadata) o;
    return Objects.equals(uuid, that.uuid)
        && Objects.equals(name, that.name)
        && Objects.equals(urls, that.urls);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uuid, name, urls);
  }

  @Override
  public String toString() {
    return "ClusterMetadata{"
        + "uuid='"
        + uuid
        + '\''
        + ", name='"
        + name
        + '\''
        + ", urls="
        + urls
        + '\''
        + '}';
  }
}
