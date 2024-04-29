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
package io.camunda.tasklist.webapp.graphql.entity;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import io.camunda.tasklist.entities.ProcessEntity;
import io.camunda.tasklist.util.CollectionUtil;

public class ProcessDTO {

  @GraphQLField @GraphQLNonNull private String id;

  @GraphQLField private String name;

  @GraphQLField private String processDefinitionId;

  private String[] sortValues;

  private boolean startedByForm;

  private String formKey;

  private String formId;

  private Boolean isFormEmbedded;

  @GraphQLField private Integer version;

  public static ProcessDTO createFrom(ProcessEntity processEntity) {
    return createFrom(processEntity, null);
  }

  public static ProcessDTO createFrom(ProcessEntity processEntity, Object[] sortValues) {
    final ProcessDTO processDTO =
        new ProcessDTO()
            .setId(processEntity.getId())
            .setName(processEntity.getName())
            .setProcessDefinitionId(processEntity.getBpmnProcessId())
            .setVersion(processEntity.getVersion())
            .setStartedByForm(processEntity.isStartedByForm())
            .setFormKey(processEntity.getFormKey())
            .setFormId(processEntity.getFormId())
            .setFormEmbedded(processEntity.getIsFormEmbedded());

    if (sortValues != null) {
      processDTO.setSortValues(CollectionUtil.toArrayOfStrings(sortValues));
    }
    return processDTO;
  }

  public String getId() {
    return id;
  }

  public ProcessDTO setId(String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public ProcessDTO setName(String name) {
    this.name = name;
    return this;
  }

  public String[] getSortValues() {
    return sortValues;
  }

  public ProcessDTO setSortValues(String[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public ProcessDTO setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public Integer getVersion() {
    return version;
  }

  public ProcessDTO setVersion(Integer version) {
    this.version = version;
    return this;
  }

  public boolean isStartedByForm() {
    return startedByForm;
  }

  public ProcessDTO setStartedByForm(boolean startedByForm) {
    this.startedByForm = startedByForm;
    return this;
  }

  public String getFormKey() {
    return formKey;
  }

  public ProcessDTO setFormKey(String formKey) {
    this.formKey = formKey;
    return this;
  }

  public Boolean getFormEmbedded() {
    return isFormEmbedded;
  }

  public ProcessDTO setFormEmbedded(Boolean formEmbedded) {
    isFormEmbedded = formEmbedded;
    return this;
  }

  public String getFormId() {
    return formId;
  }

  public ProcessDTO setFormId(String formId) {
    this.formId = formId;
    return this;
  }
}
