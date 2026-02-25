/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.camunda.zeebe.model.bpmn.instance;

import io.camunda.zeebe.model.bpmn.instance.bpmndi.BpmnDiagram;
import java.util.Collection;

/**
 * The BPMN definitions element
 *
 * @author Daniel Meyer
 */
public interface Definitions extends IdentifiableBpmnElement, NamedBpmnElement {

  String getTargetNamespace();

  void setTargetNamespace(String namespace);

  String getExpressionLanguage();

  void setExpressionLanguage(String expressionLanguage);

  String getTypeLanguage();

  void setTypeLanguage(String typeLanguage);

  String getExporter();

  void setExporter(String exporter);

  String getExporterVersion();

  void setExporterVersion(String exporterVersion);

  Collection<Import> getImports();

  Collection<Extension> getExtensions();

  Collection<RootElement> getRootElements();

  Collection<BpmnDiagram> getBpmDiagrams();

  Collection<Relationship> getRelationships();
}
