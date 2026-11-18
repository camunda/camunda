/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
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
package org.camunda.bpm.model.dmn;

import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN11_ALTERNATIVE_NS;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN11_NS;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN12_NS;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN13_ALTERNATIVE_NS;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN13_NS;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN14_NS;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN15_NS;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.camunda.bpm.model.dmn.impl.DmnParser;
import org.camunda.bpm.model.dmn.impl.instance.AllowedAnswersImpl;
import org.camunda.bpm.model.dmn.impl.instance.AllowedValuesImpl;
import org.camunda.bpm.model.dmn.impl.instance.ArtifactImpl;
import org.camunda.bpm.model.dmn.impl.instance.AssociationImpl;
import org.camunda.bpm.model.dmn.impl.instance.AuthorityRequirementImpl;
import org.camunda.bpm.model.dmn.impl.instance.BindingImpl;
import org.camunda.bpm.model.dmn.impl.instance.BusinessContextElementImpl;
import org.camunda.bpm.model.dmn.impl.instance.BusinessKnowledgeModelImpl;
import org.camunda.bpm.model.dmn.impl.instance.ColumnImpl;
import org.camunda.bpm.model.dmn.impl.instance.ContextEntryImpl;
import org.camunda.bpm.model.dmn.impl.instance.ContextImpl;
import org.camunda.bpm.model.dmn.impl.instance.DecisionImpl;
import org.camunda.bpm.model.dmn.impl.instance.DecisionMadeReferenceImpl;
import org.camunda.bpm.model.dmn.impl.instance.DecisionMakerReferenceImpl;
import org.camunda.bpm.model.dmn.impl.instance.DecisionOwnedReferenceImpl;
import org.camunda.bpm.model.dmn.impl.instance.DecisionOwnerReferenceImpl;
import org.camunda.bpm.model.dmn.impl.instance.DecisionRuleImpl;
import org.camunda.bpm.model.dmn.impl.instance.DecisionServiceImpl;
import org.camunda.bpm.model.dmn.impl.instance.DecisionTableImpl;
import org.camunda.bpm.model.dmn.impl.instance.DefaultOutputEntryImpl;
import org.camunda.bpm.model.dmn.impl.instance.DefinitionsImpl;
import org.camunda.bpm.model.dmn.impl.instance.DescriptionImpl;
import org.camunda.bpm.model.dmn.impl.instance.DmnElementImpl;
import org.camunda.bpm.model.dmn.impl.instance.DmnElementReferenceImpl;
import org.camunda.bpm.model.dmn.impl.instance.DrgElementImpl;
import org.camunda.bpm.model.dmn.impl.instance.DrgElementReferenceImpl;
import org.camunda.bpm.model.dmn.impl.instance.ElementCollectionImpl;
import org.camunda.bpm.model.dmn.impl.instance.EncapsulatedDecisionReferenceImpl;
import org.camunda.bpm.model.dmn.impl.instance.EncapsulatedLogicImpl;
import org.camunda.bpm.model.dmn.impl.instance.ExpressionImpl;
import org.camunda.bpm.model.dmn.impl.instance.ExtensionElementsImpl;
import org.camunda.bpm.model.dmn.impl.instance.FormalParameterImpl;
import org.camunda.bpm.model.dmn.impl.instance.FunctionDefinitionImpl;
import org.camunda.bpm.model.dmn.impl.instance.ImpactedPerformanceIndicatorReferenceImpl;
import org.camunda.bpm.model.dmn.impl.instance.ImpactingDecisionReferenceImpl;
import org.camunda.bpm.model.dmn.impl.instance.ImportImpl;
import org.camunda.bpm.model.dmn.impl.instance.ImportedElementImpl;
import org.camunda.bpm.model.dmn.impl.instance.ImportedValuesImpl;
import org.camunda.bpm.model.dmn.impl.instance.InformationItemImpl;
import org.camunda.bpm.model.dmn.impl.instance.InformationRequirementImpl;
import org.camunda.bpm.model.dmn.impl.instance.InputClauseImpl;
import org.camunda.bpm.model.dmn.impl.instance.InputDataImpl;
import org.camunda.bpm.model.dmn.impl.instance.InputDataReferenceImpl;
import org.camunda.bpm.model.dmn.impl.instance.InputDecisionReferenceImpl;
import org.camunda.bpm.model.dmn.impl.instance.InputEntryImpl;
import org.camunda.bpm.model.dmn.impl.instance.InputExpressionImpl;
import org.camunda.bpm.model.dmn.impl.instance.InputImpl;
import org.camunda.bpm.model.dmn.impl.instance.InputValuesImpl;
import org.camunda.bpm.model.dmn.impl.instance.InvocationImpl;
import org.camunda.bpm.model.dmn.impl.instance.ItemComponentImpl;
import org.camunda.bpm.model.dmn.impl.instance.ItemDefinitionImpl;
import org.camunda.bpm.model.dmn.impl.instance.ItemDefinitionReferenceImpl;
import org.camunda.bpm.model.dmn.impl.instance.KnowledgeRequirementImpl;
import org.camunda.bpm.model.dmn.impl.instance.KnowledgeSourceImpl;
import org.camunda.bpm.model.dmn.impl.instance.ListImpl;
import org.camunda.bpm.model.dmn.impl.instance.LiteralExpressionImpl;
import org.camunda.bpm.model.dmn.impl.instance.NamedElementImpl;
import org.camunda.bpm.model.dmn.impl.instance.OrganizationUnitImpl;
import org.camunda.bpm.model.dmn.impl.instance.OutputClauseImpl;
import org.camunda.bpm.model.dmn.impl.instance.OutputDecisionReferenceImpl;
import org.camunda.bpm.model.dmn.impl.instance.OutputEntryImpl;
import org.camunda.bpm.model.dmn.impl.instance.OutputImpl;
import org.camunda.bpm.model.dmn.impl.instance.OutputValuesImpl;
import org.camunda.bpm.model.dmn.impl.instance.OwnerReferenceImpl;
import org.camunda.bpm.model.dmn.impl.instance.ParameterImpl;
import org.camunda.bpm.model.dmn.impl.instance.PerformanceIndicatorImpl;
import org.camunda.bpm.model.dmn.impl.instance.QuestionImpl;
import org.camunda.bpm.model.dmn.impl.instance.RelationImpl;
import org.camunda.bpm.model.dmn.impl.instance.RequiredAuthorityReferenceImpl;
import org.camunda.bpm.model.dmn.impl.instance.RequiredDecisionReferenceImpl;
import org.camunda.bpm.model.dmn.impl.instance.RequiredInputReferenceImpl;
import org.camunda.bpm.model.dmn.impl.instance.RequiredKnowledgeReferenceImpl;
import org.camunda.bpm.model.dmn.impl.instance.RowImpl;
import org.camunda.bpm.model.dmn.impl.instance.RuleImpl;
import org.camunda.bpm.model.dmn.impl.instance.SourceRefImpl;
import org.camunda.bpm.model.dmn.impl.instance.SupportedObjectiveReferenceImpl;
import org.camunda.bpm.model.dmn.impl.instance.TargetRefImpl;
import org.camunda.bpm.model.dmn.impl.instance.TextAnnotationImpl;
import org.camunda.bpm.model.dmn.impl.instance.TextImpl;
import org.camunda.bpm.model.dmn.impl.instance.TypeImpl;
import org.camunda.bpm.model.dmn.impl.instance.TypeRefImpl;
import org.camunda.bpm.model.dmn.impl.instance.UnaryTestsImpl;
import org.camunda.bpm.model.dmn.impl.instance.UsingProcessReferenceImpl;
import org.camunda.bpm.model.dmn.impl.instance.UsingTaskReferenceImpl;
import org.camunda.bpm.model.dmn.impl.instance.VariableImpl;
import org.camunda.bpm.model.xml.Model;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.ModelException;
import org.camunda.bpm.model.xml.ModelParseException;
import org.camunda.bpm.model.xml.ModelValidationException;
import org.camunda.bpm.model.xml.impl.instance.ModelElementInstanceImpl;
import org.camunda.bpm.model.xml.impl.util.IoUtil;

public class Dmn {

  /** the singleton instance of {@link Dmn}. If you want to customize the behavior of Dmn,
   * replace this instance with an instance of a custom subclass of {@link Dmn}. */
  public static Dmn INSTANCE = new Dmn();

  /** the parser used by the Dmn implementation. */
  private DmnParser dmnParser = new DmnParser();
  private final ModelBuilder dmnModelBuilder;

  /** The {@link Model}
   */
  private Model dmnModel;

  /**
   * Allows reading a {@link DmnModelInstance} from a File.
   *
   * @param file the {@link File} to read the {@link DmnModelInstance} from
   * @return the model read
   * @throws DmnModelException if the model cannot be read
   */
  public static DmnModelInstance readModelFromFile(File file) {
    return INSTANCE.doReadModelFromFile(file);
  }

  /**
   * Allows reading a {@link DmnModelInstance} from an {@link InputStream}
   *
   * @param stream the {@link InputStream} to read the {@link DmnModelInstance} from
   * @return the model read
   * @throws ModelParseException if the model cannot be read
   */
  public static DmnModelInstance readModelFromStream(InputStream stream) {
    return INSTANCE.doReadModelFromInputStream(stream);
  }

  /**
   * Allows writing a {@link DmnModelInstance} to a File. It will be
   * validated before writing.
   *
   * @param file the {@link File} to write the {@link DmnModelInstance} to
   * @param modelInstance the {@link DmnModelInstance} to write
   * @throws DmnModelException if the model cannot be written
   * @throws ModelValidationException if the model is not valid
   */
  public static void writeModelToFile(File file, DmnModelInstance modelInstance) {
    INSTANCE.doWriteModelToFile(file, modelInstance);
  }

  /**
   * Allows writing a {@link DmnModelInstance} to an {@link OutputStream}. It will be
   * validated before writing.
   *
   * @param stream the {@link OutputStream} to write the {@link DmnModelInstance} to
   * @param modelInstance the {@link DmnModelInstance} to write
   * @throws ModelException if the model cannot be written
   * @throws ModelValidationException if the model is not valid
   */
  public static void writeModelToStream(OutputStream stream, DmnModelInstance modelInstance) {
    INSTANCE.doWriteModelToOutputStream(stream, modelInstance);
  }

  /**
   * Allows the conversion of a {@link DmnModelInstance} to an {@link String}. It will
   * be validated before conversion.
   *
   * @param modelInstance  the model instance to convert
   * @return the XML string representation of the model instance
   */
  public static String convertToString(DmnModelInstance modelInstance) {
    return INSTANCE.doConvertToString(modelInstance);
  }

  /**
   * Validate model DOM document
   *
   * @param modelInstance the {@link DmnModelInstance} to validate
   * @throws ModelValidationException if the model is not valid
   */
  public static void validateModel(DmnModelInstance modelInstance) {
    INSTANCE.doValidateModel(modelInstance);
  }

  /**
   * Allows creating an new, empty {@link DmnModelInstance}.
   *
   * @return the empty model.
   */
  public static DmnModelInstance createEmptyModel() {
    return INSTANCE.doCreateEmptyModel();
  }

  /**
   * Register known types of the Dmn model
   */
  protected Dmn() {
    dmnModelBuilder = ModelBuilder.createInstance("DMN Model");
    dmnModelBuilder.alternativeNamespace(DMN15_NS, DMN13_NS);
    dmnModelBuilder.alternativeNamespace(DMN14_NS, DMN13_NS);
    dmnModelBuilder.alternativeNamespace(DMN13_ALTERNATIVE_NS, DMN13_NS);
    dmnModelBuilder.alternativeNamespace(DMN12_NS, DMN13_NS);
    dmnModelBuilder.alternativeNamespace(DMN11_NS, DMN13_NS);
    dmnModelBuilder.alternativeNamespace(DMN11_ALTERNATIVE_NS, DMN13_NS);
    doRegisterTypes(dmnModelBuilder);
    dmnModel = dmnModelBuilder.build();
  }

  protected DmnModelInstance doReadModelFromFile(File file) {
    InputStream is = null;
    try {
      is = new FileInputStream(file);
      return doReadModelFromInputStream(is);

    } catch (FileNotFoundException e) {
      throw new DmnModelException("Cannot read model from file "+file+": file does not exist.");

    } finally {
      IoUtil.closeSilently(is);

    }
  }

  protected DmnModelInstance doReadModelFromInputStream(InputStream is) {
    return dmnParser.parseModelFromStream(is);
  }

  protected void doWriteModelToFile(File file, DmnModelInstance modelInstance) {
    OutputStream os = null;
    try {
      os = new FileOutputStream(file);
      doWriteModelToOutputStream(os, modelInstance);
    }
    catch (FileNotFoundException e) {
      throw new DmnModelException("Cannot write model to file "+file+": file does not exist.");
    } finally {
      IoUtil.closeSilently(os);
    }
  }

  protected void doWriteModelToOutputStream(OutputStream os, DmnModelInstance modelInstance) {
    // validate DOM document
    doValidateModel(modelInstance);
    // write XML
    IoUtil.writeDocumentToOutputStream(modelInstance.getDocument(), os);
  }

  protected String doConvertToString(DmnModelInstance modelInstance) {
    // validate DOM document
    doValidateModel(modelInstance);
    // convert to XML string
    return IoUtil.convertXmlDocumentToString(modelInstance.getDocument());
  }

  protected void doValidateModel(DmnModelInstance modelInstance) {
    dmnParser.validateModel(modelInstance.getDocument());
  }

  protected DmnModelInstance doCreateEmptyModel() {
    return dmnParser.getEmptyModel();
  }

  protected void doRegisterTypes(ModelBuilder modelBuilder) {

    AllowedAnswersImpl.registerType(modelBuilder);
    AllowedValuesImpl.registerType(modelBuilder);
    ArtifactImpl.registerType(modelBuilder);
    AssociationImpl.registerType(modelBuilder);
    AuthorityRequirementImpl.registerType(modelBuilder);
    BindingImpl.registerType(modelBuilder);
    BusinessContextElementImpl.registerType(modelBuilder);
    BusinessKnowledgeModelImpl.registerType(modelBuilder);
    ColumnImpl.registerType(modelBuilder);
    ContextEntryImpl.registerType(modelBuilder);
    ContextImpl.registerType(modelBuilder);
    DecisionImpl.registerType(modelBuilder);
    DecisionMadeReferenceImpl.registerType(modelBuilder);
    DecisionMakerReferenceImpl.registerType(modelBuilder);
    DecisionOwnedReferenceImpl.registerType(modelBuilder);
    DecisionOwnerReferenceImpl.registerType(modelBuilder);
    DecisionRuleImpl.registerType(modelBuilder);
    DecisionServiceImpl.registerType(modelBuilder);
    DecisionTableImpl.registerType(modelBuilder);
    DefaultOutputEntryImpl.registerType(modelBuilder);
    DefinitionsImpl.registerType(modelBuilder);
    DescriptionImpl.registerType(modelBuilder);
    DmnElementImpl.registerType(modelBuilder);
    DmnElementReferenceImpl.registerType(modelBuilder);
    DrgElementImpl.registerType(modelBuilder);
    DrgElementReferenceImpl.registerType(modelBuilder);
    ElementCollectionImpl.registerType(modelBuilder);
    EncapsulatedDecisionReferenceImpl.registerType(modelBuilder);
    EncapsulatedLogicImpl.registerType(modelBuilder);
    ExpressionImpl.registerType(modelBuilder);
    ExtensionElementsImpl.registerType(modelBuilder);
    FormalParameterImpl.registerType(modelBuilder);
    FunctionDefinitionImpl.registerType(modelBuilder);
    ImpactedPerformanceIndicatorReferenceImpl.registerType(modelBuilder);
    ImpactingDecisionReferenceImpl.registerType(modelBuilder);
    ImportImpl.registerType(modelBuilder);
    ImportedElementImpl.registerType(modelBuilder);
    ImportedValuesImpl.registerType(modelBuilder);
    InformationItemImpl.registerType(modelBuilder);
    InformationRequirementImpl.registerType(modelBuilder);
    InputImpl.registerType(modelBuilder);
    InputClauseImpl.registerType(modelBuilder);
    InputDataImpl.registerType(modelBuilder);
    InputDataReferenceImpl.registerType(modelBuilder);
    InputDecisionReferenceImpl.registerType(modelBuilder);
    InputEntryImpl.registerType(modelBuilder);
    InputExpressionImpl.registerType(modelBuilder);
    InputValuesImpl.registerType(modelBuilder);
    InvocationImpl.registerType(modelBuilder);
    ItemComponentImpl.registerType(modelBuilder);
    ItemDefinitionImpl.registerType(modelBuilder);
    ItemDefinitionReferenceImpl.registerType(modelBuilder);
    KnowledgeRequirementImpl.registerType(modelBuilder);
    KnowledgeSourceImpl.registerType(modelBuilder);
    ListImpl.registerType(modelBuilder);
    LiteralExpressionImpl.registerType(modelBuilder);
    ModelElementInstanceImpl.registerType(modelBuilder);
    NamedElementImpl.registerType(modelBuilder);
    OrganizationUnitImpl.registerType(modelBuilder);
    OutputImpl.registerType(modelBuilder);
    OutputClauseImpl.registerType(modelBuilder);
    OutputDecisionReferenceImpl.registerType(modelBuilder);
    OutputEntryImpl.registerType(modelBuilder);
    OutputValuesImpl.registerType(modelBuilder);
    OwnerReferenceImpl.registerType(modelBuilder);
    ParameterImpl.registerType(modelBuilder);
    PerformanceIndicatorImpl.registerType(modelBuilder);
    QuestionImpl.registerType(modelBuilder);
    RelationImpl.registerType(modelBuilder);
    RequiredAuthorityReferenceImpl.registerType(modelBuilder);
    RequiredDecisionReferenceImpl.registerType(modelBuilder);
    RequiredInputReferenceImpl.registerType(modelBuilder);
    RequiredKnowledgeReferenceImpl.registerType(modelBuilder);
    RowImpl.registerType(modelBuilder);
    RuleImpl.registerType(modelBuilder);
    SourceRefImpl.registerType(modelBuilder);
    SupportedObjectiveReferenceImpl.registerType(modelBuilder);
    TargetRefImpl.registerType(modelBuilder);
    TextImpl.registerType(modelBuilder);
    TextAnnotationImpl.registerType(modelBuilder);
    TypeImpl.registerType(modelBuilder);
    TypeRefImpl.registerType(modelBuilder);
    UnaryTestsImpl.registerType(modelBuilder);
    UsingProcessReferenceImpl.registerType(modelBuilder);
    UsingTaskReferenceImpl.registerType(modelBuilder);
    VariableImpl.registerType(modelBuilder);

    /** camunda extensions */
  }

  /**
   * @return the {@link Model} instance to use
   */
  public Model getDmnModel() {
    return dmnModel;
  }

  public ModelBuilder getDmnModelBuilder() {
    return dmnModelBuilder;
  }

  /**
   * @param dmnModel the cmmnModel to set
   */
  public void setDmnModel(Model dmnModel) {
    this.dmnModel = dmnModel;
  }

}
