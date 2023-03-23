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

package io.camunda.zeebe.model.bpmn;

import static io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN20_NS;

import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.model.bpmn.impl.BpmnImpl;
import io.camunda.zeebe.model.bpmn.impl.BpmnParser;
import io.camunda.zeebe.model.bpmn.impl.instance.ActivationConditionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ActivityImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.AdHocSubProcessImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ArtifactImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.AssignmentImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.AssociationImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.AuditingImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.BaseElementImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.BoundaryEventImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.BusinessRuleTaskImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.CallActivityImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.CallConversationImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.CallableElementImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.CancelEventDefinitionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.CatchEventImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.CategoryImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.CategoryValueImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.CategoryValueRef;
import io.camunda.zeebe.model.bpmn.impl.instance.ChildLaneSet;
import io.camunda.zeebe.model.bpmn.impl.instance.CollaborationImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.CompensateEventDefinitionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.CompletionConditionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ComplexBehaviorDefinitionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ComplexGatewayImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ConditionExpressionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ConditionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ConditionalEventDefinitionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ConversationAssociationImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ConversationImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ConversationLinkImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ConversationNodeImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.CorrelationKeyImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.CorrelationPropertyBindingImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.CorrelationPropertyImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.CorrelationPropertyRef;
import io.camunda.zeebe.model.bpmn.impl.instance.CorrelationPropertyRetrievalExpressionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.CorrelationSubscriptionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.DataAssociationImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.DataInputAssociationImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.DataInputImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.DataInputRefs;
import io.camunda.zeebe.model.bpmn.impl.instance.DataObjectImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.DataObjectReferenceImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.DataOutputAssociationImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.DataOutputImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.DataOutputRefs;
import io.camunda.zeebe.model.bpmn.impl.instance.DataPath;
import io.camunda.zeebe.model.bpmn.impl.instance.DataStateImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.DataStoreImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.DataStoreReferenceImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.DefinitionsImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.DocumentationImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.EndEventImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.EndPointImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.EndPointRef;
import io.camunda.zeebe.model.bpmn.impl.instance.ErrorEventDefinitionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ErrorImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ErrorRef;
import io.camunda.zeebe.model.bpmn.impl.instance.EscalationEventDefinitionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.EscalationImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.EventBasedGatewayImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.EventDefinitionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.EventDefinitionRef;
import io.camunda.zeebe.model.bpmn.impl.instance.EventImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ExclusiveGatewayImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ExpressionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ExtensionElementsImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ExtensionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.FlowElementImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.FlowNodeImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.FlowNodeRef;
import io.camunda.zeebe.model.bpmn.impl.instance.FormalExpressionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.From;
import io.camunda.zeebe.model.bpmn.impl.instance.GatewayImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.GlobalConversationImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.GroupImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.HumanPerformerImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ImportImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.InMessageRef;
import io.camunda.zeebe.model.bpmn.impl.instance.InclusiveGatewayImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.Incoming;
import io.camunda.zeebe.model.bpmn.impl.instance.InnerParticipantRef;
import io.camunda.zeebe.model.bpmn.impl.instance.InputDataItemImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.InputSetImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.InputSetRefs;
import io.camunda.zeebe.model.bpmn.impl.instance.InteractionNodeImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.InterfaceImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.InterfaceRef;
import io.camunda.zeebe.model.bpmn.impl.instance.IntermediateCatchEventImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.IntermediateThrowEventImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.IoBindingImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.IoSpecificationImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ItemAwareElementImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ItemDefinitionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.LaneImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.LaneSetImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.LinkEventDefinitionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.LoopCardinalityImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.LoopCharacteristicsImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.LoopDataInputRef;
import io.camunda.zeebe.model.bpmn.impl.instance.LoopDataOutputRef;
import io.camunda.zeebe.model.bpmn.impl.instance.ManualTaskImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.MessageEventDefinitionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.MessageFlowAssociationImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.MessageFlowImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.MessageFlowRef;
import io.camunda.zeebe.model.bpmn.impl.instance.MessageImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.MessagePath;
import io.camunda.zeebe.model.bpmn.impl.instance.MonitoringImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.MultiInstanceLoopCharacteristicsImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.OperationImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.OperationRef;
import io.camunda.zeebe.model.bpmn.impl.instance.OptionalInputRefs;
import io.camunda.zeebe.model.bpmn.impl.instance.OptionalOutputRefs;
import io.camunda.zeebe.model.bpmn.impl.instance.OutMessageRef;
import io.camunda.zeebe.model.bpmn.impl.instance.OuterParticipantRef;
import io.camunda.zeebe.model.bpmn.impl.instance.Outgoing;
import io.camunda.zeebe.model.bpmn.impl.instance.OutputDataItemImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.OutputSetImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.OutputSetRefs;
import io.camunda.zeebe.model.bpmn.impl.instance.ParallelGatewayImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ParticipantAssociationImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ParticipantImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ParticipantMultiplicityImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ParticipantRef;
import io.camunda.zeebe.model.bpmn.impl.instance.PartitionElement;
import io.camunda.zeebe.model.bpmn.impl.instance.PerformerImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.PotentialOwnerImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ProcessImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.PropertyImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ReceiveTaskImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.RelationshipImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.RenderingImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ResourceAssignmentExpressionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ResourceImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ResourceParameterBindingImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ResourceParameterImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ResourceRef;
import io.camunda.zeebe.model.bpmn.impl.instance.ResourceRoleImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.RootElementImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ScriptImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ScriptTaskImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.SendTaskImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.SequenceFlowImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ServiceTaskImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.SignalEventDefinitionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.SignalImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.Source;
import io.camunda.zeebe.model.bpmn.impl.instance.SourceRef;
import io.camunda.zeebe.model.bpmn.impl.instance.StartEventImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.SubConversationImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.SubProcessImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.SupportedInterfaceRef;
import io.camunda.zeebe.model.bpmn.impl.instance.Supports;
import io.camunda.zeebe.model.bpmn.impl.instance.Target;
import io.camunda.zeebe.model.bpmn.impl.instance.TargetRef;
import io.camunda.zeebe.model.bpmn.impl.instance.TaskImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.TerminateEventDefinitionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.TextAnnotationImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.TextImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ThrowEventImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.TimeCycleImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.TimeDateImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.TimeDurationImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.TimerEventDefinitionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.To;
import io.camunda.zeebe.model.bpmn.impl.instance.TransactionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.Transformation;
import io.camunda.zeebe.model.bpmn.impl.instance.UserTaskImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.WhileExecutingInputRefs;
import io.camunda.zeebe.model.bpmn.impl.instance.WhileExecutingOutputRefs;
import io.camunda.zeebe.model.bpmn.impl.instance.bpmndi.BpmnDiagramImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.bpmndi.BpmnEdgeImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.bpmndi.BpmnLabelImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.bpmndi.BpmnLabelStyleImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.bpmndi.BpmnPlaneImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.bpmndi.BpmnShapeImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.dc.BoundsImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.dc.FontImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.dc.PointImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.di.DiagramElementImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.di.DiagramImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.di.EdgeImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.di.LabelImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.di.LabeledEdgeImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.di.LabeledShapeImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.di.NodeImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.di.PlaneImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.di.ShapeImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.di.StyleImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.di.WaypointImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.zeebe.ZeebeAssignmentDefinitionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.zeebe.ZeebeCalledDecisionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.zeebe.ZeebeCalledElementImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.zeebe.ZeebeFormDefinitionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.zeebe.ZeebeHeaderImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.zeebe.ZeebeInputImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.zeebe.ZeebeIoMappingImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.zeebe.ZeebeLoopCharacteristicsImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.zeebe.ZeebeOutputImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.zeebe.ZeebePropertiesImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.zeebe.ZeebePropertyImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.zeebe.ZeebeScriptImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.zeebe.ZeebeSubscriptionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.zeebe.ZeebeTaskDefinitionImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.zeebe.ZeebeTaskHeadersImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.zeebe.ZeebeTaskScheduleImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.zeebe.ZeebeUserTaskFormImpl;
import io.camunda.zeebe.model.bpmn.instance.Definitions;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.bpmndi.BpmnDiagram;
import io.camunda.zeebe.model.bpmn.instance.bpmndi.BpmnPlane;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import org.camunda.bpm.model.xml.Model;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.ModelException;
import org.camunda.bpm.model.xml.ModelParseException;
import org.camunda.bpm.model.xml.ModelValidationException;
import org.camunda.bpm.model.xml.impl.instance.ModelElementInstanceImpl;
import org.camunda.bpm.model.xml.impl.util.IoUtil;

/**
 * Provides access to the camunda BPMN model api.
 *
 * @author Daniel Meyer
 */
public class Bpmn {

  public static final Bpmn INSTANCE = new BpmnImpl();

  /** the parser used by the Bpmn implementation. */
  private final BpmnParser bpmnParser = new BpmnParser();

  private final ModelBuilder bpmnModelBuilder;

  /** The {@link Model} */
  private Model bpmnModel;

  /** Register known types of the BPMN model */
  protected Bpmn() {
    bpmnModelBuilder = ModelBuilder.createInstance("BPMN Model");
    doRegisterTypes(bpmnModelBuilder);
    bpmnModel = bpmnModelBuilder.build();
  }

  /**
   * Allows reading a {@link BpmnModelInstance} from a File.
   *
   * @param file the {@link File} to read the {@link BpmnModelInstance} from
   * @return the model read
   * @throws BpmnModelException if the model cannot be read
   */
  public static BpmnModelInstance readModelFromFile(final File file) {
    return INSTANCE.doReadModelFromFile(file);
  }

  /**
   * Allows reading a {@link BpmnModelInstance} from an {@link InputStream}
   *
   * @param stream the {@link InputStream} to read the {@link BpmnModelInstance} from
   * @return the model read
   * @throws ModelParseException if the model cannot be read
   */
  public static BpmnModelInstance readModelFromStream(final InputStream stream) {
    return INSTANCE.doReadModelFromInputStream(stream);
  }

  /**
   * Allows writing a {@link BpmnModelInstance} to a File. It will be validated before writing.
   *
   * @param file the {@link File} to write the {@link BpmnModelInstance} to
   * @param modelInstance the {@link BpmnModelInstance} to write
   * @throws BpmnModelException if the model cannot be written
   * @throws ModelValidationException if the model is not valid
   */
  public static void writeModelToFile(final File file, final BpmnModelInstance modelInstance) {
    INSTANCE.doWriteModelToFile(file, modelInstance);
  }

  /**
   * Allows writing a {@link BpmnModelInstance} to an {@link OutputStream}. It will be validated
   * before writing.
   *
   * @param stream the {@link OutputStream} to write the {@link BpmnModelInstance} to
   * @param modelInstance the {@link BpmnModelInstance} to write
   * @throws ModelException if the model cannot be written
   * @throws ModelValidationException if the model is not valid
   */
  public static void writeModelToStream(
      final OutputStream stream, final BpmnModelInstance modelInstance) {
    INSTANCE.doWriteModelToOutputStream(stream, modelInstance);
  }

  /**
   * Allows the conversion of a {@link BpmnModelInstance} to an {@link String}. It will be validated
   * before conversion.
   *
   * @param modelInstance the model instance to convert
   * @return the XML string representation of the model instance
   */
  public static String convertToString(final BpmnModelInstance modelInstance) {
    return INSTANCE.doConvertToString(modelInstance);
  }

  /**
   * Validate model DOM document
   *
   * @param modelInstance the {@link BpmnModelInstance} to validate
   * @throws ModelValidationException if the model is not valid
   */
  public static void validateModel(final BpmnModelInstance modelInstance) {
    INSTANCE.doValidateModel(modelInstance);
  }

  /**
   * Allows creating an new, empty {@link BpmnModelInstance}.
   *
   * @return the empty model.
   */
  public static BpmnModelInstance createEmptyModel() {
    return INSTANCE.doCreateEmptyModel();
  }

  public static ProcessBuilder createProcess() {
    final BpmnModelInstance modelInstance = INSTANCE.doCreateEmptyModel();
    final Definitions definitions = modelInstance.newInstance(Definitions.class);
    definitions.setTargetNamespace(BPMN20_NS);
    modelInstance.setDefinitions(definitions);
    final Process process = modelInstance.newInstance(Process.class);
    definitions.addChildElement(process);

    final BpmnDiagram bpmnDiagram = modelInstance.newInstance(BpmnDiagram.class);

    final BpmnPlane bpmnPlane = modelInstance.newInstance(BpmnPlane.class);
    bpmnPlane.setBpmnElement(process);

    bpmnDiagram.addChildElement(bpmnPlane);
    definitions.addChildElement(bpmnDiagram);

    return process.builder();
  }

  public static ProcessBuilder createProcess(final String processId) {
    return createProcess().id(processId);
  }

  public static ProcessBuilder createExecutableProcess() {
    return createProcess().executable();
  }

  public static ProcessBuilder createExecutableProcess(final String processId) {
    return createProcess(processId).executable();
  }

  protected BpmnModelInstance doReadModelFromFile(final File file) {
    InputStream is = null;
    try {
      is = new FileInputStream(file);
      return doReadModelFromInputStream(is);

    } catch (final FileNotFoundException e) {
      throw new BpmnModelException(
          "Cannot read model from file " + file + ": file does not exist.");

    } finally {
      IoUtil.closeSilently(is);
    }
  }

  protected BpmnModelInstance doReadModelFromInputStream(final InputStream is) {
    return bpmnParser.parseModelFromStream(is);
  }

  protected void doWriteModelToFile(final File file, final BpmnModelInstance modelInstance) {
    OutputStream os = null;
    try {
      os = new FileOutputStream(file);
      doWriteModelToOutputStream(os, modelInstance);
    } catch (final FileNotFoundException e) {
      throw new BpmnModelException("Cannot write model to file " + file + ": file does not exist.");
    } finally {
      IoUtil.closeSilently(os);
    }
  }

  protected void doWriteModelToOutputStream(
      final OutputStream os, final BpmnModelInstance modelInstance) {
    // validate DOM document
    doValidateModel(modelInstance);
    // write XML
    IoUtil.writeDocumentToOutputStream(modelInstance.getDocument(), os);
  }

  protected String doConvertToString(final BpmnModelInstance modelInstance) {
    // validate DOM document
    doValidateModel(modelInstance);
    // convert to XML string
    return IoUtil.convertXmlDocumentToString(modelInstance.getDocument());
  }

  protected void doValidateModel(final BpmnModelInstance modelInstance) {
    bpmnParser.validateModel(modelInstance.getDocument());
  }

  protected BpmnModelInstance doCreateEmptyModel() {
    return bpmnParser.getEmptyModel();
  }

  protected void doRegisterTypes(final ModelBuilder bpmnModelBuilder) {
    ActivationConditionImpl.registerType(bpmnModelBuilder);
    ActivityImpl.registerType(bpmnModelBuilder);
    AdHocSubProcessImpl.registerType(bpmnModelBuilder);
    ArtifactImpl.registerType(bpmnModelBuilder);
    AssignmentImpl.registerType(bpmnModelBuilder);
    AssociationImpl.registerType(bpmnModelBuilder);
    AuditingImpl.registerType(bpmnModelBuilder);
    BaseElementImpl.registerType(bpmnModelBuilder);
    BoundaryEventImpl.registerType(bpmnModelBuilder);
    BusinessRuleTaskImpl.registerType(bpmnModelBuilder);
    CallableElementImpl.registerType(bpmnModelBuilder);
    CallActivityImpl.registerType(bpmnModelBuilder);
    CallConversationImpl.registerType(bpmnModelBuilder);
    CancelEventDefinitionImpl.registerType(bpmnModelBuilder);
    CatchEventImpl.registerType(bpmnModelBuilder);
    CategoryImpl.registerType(bpmnModelBuilder);
    CategoryValueImpl.registerType(bpmnModelBuilder);
    CategoryValueRef.registerType(bpmnModelBuilder);
    ChildLaneSet.registerType(bpmnModelBuilder);
    CollaborationImpl.registerType(bpmnModelBuilder);
    CompensateEventDefinitionImpl.registerType(bpmnModelBuilder);
    ConditionImpl.registerType(bpmnModelBuilder);
    ConditionalEventDefinitionImpl.registerType(bpmnModelBuilder);
    CompletionConditionImpl.registerType(bpmnModelBuilder);
    ComplexBehaviorDefinitionImpl.registerType(bpmnModelBuilder);
    ComplexGatewayImpl.registerType(bpmnModelBuilder);
    ConditionExpressionImpl.registerType(bpmnModelBuilder);
    ConversationAssociationImpl.registerType(bpmnModelBuilder);
    ConversationImpl.registerType(bpmnModelBuilder);
    ConversationLinkImpl.registerType(bpmnModelBuilder);
    ConversationNodeImpl.registerType(bpmnModelBuilder);
    CorrelationKeyImpl.registerType(bpmnModelBuilder);
    CorrelationPropertyBindingImpl.registerType(bpmnModelBuilder);
    CorrelationPropertyImpl.registerType(bpmnModelBuilder);
    CorrelationPropertyRef.registerType(bpmnModelBuilder);
    CorrelationPropertyRetrievalExpressionImpl.registerType(bpmnModelBuilder);
    CorrelationSubscriptionImpl.registerType(bpmnModelBuilder);
    DataAssociationImpl.registerType(bpmnModelBuilder);
    DataInputAssociationImpl.registerType(bpmnModelBuilder);
    DataInputImpl.registerType(bpmnModelBuilder);
    DataInputRefs.registerType(bpmnModelBuilder);
    DataOutputAssociationImpl.registerType(bpmnModelBuilder);
    DataOutputImpl.registerType(bpmnModelBuilder);
    DataOutputRefs.registerType(bpmnModelBuilder);
    DataPath.registerType(bpmnModelBuilder);
    DataStateImpl.registerType(bpmnModelBuilder);
    DataObjectImpl.registerType(bpmnModelBuilder);
    DataObjectReferenceImpl.registerType(bpmnModelBuilder);
    DataStoreImpl.registerType(bpmnModelBuilder);
    DataStoreReferenceImpl.registerType(bpmnModelBuilder);
    DefinitionsImpl.registerType(bpmnModelBuilder);
    DocumentationImpl.registerType(bpmnModelBuilder);
    EndEventImpl.registerType(bpmnModelBuilder);
    EndPointImpl.registerType(bpmnModelBuilder);
    EndPointRef.registerType(bpmnModelBuilder);
    ErrorEventDefinitionImpl.registerType(bpmnModelBuilder);
    ErrorImpl.registerType(bpmnModelBuilder);
    ErrorRef.registerType(bpmnModelBuilder);
    EscalationImpl.registerType(bpmnModelBuilder);
    EscalationEventDefinitionImpl.registerType(bpmnModelBuilder);
    EventBasedGatewayImpl.registerType(bpmnModelBuilder);
    EventDefinitionImpl.registerType(bpmnModelBuilder);
    EventDefinitionRef.registerType(bpmnModelBuilder);
    EventImpl.registerType(bpmnModelBuilder);
    ExclusiveGatewayImpl.registerType(bpmnModelBuilder);
    ExpressionImpl.registerType(bpmnModelBuilder);
    ExtensionElementsImpl.registerType(bpmnModelBuilder);
    ExtensionImpl.registerType(bpmnModelBuilder);
    FlowElementImpl.registerType(bpmnModelBuilder);
    FlowNodeImpl.registerType(bpmnModelBuilder);
    FlowNodeRef.registerType(bpmnModelBuilder);
    FormalExpressionImpl.registerType(bpmnModelBuilder);
    From.registerType(bpmnModelBuilder);
    GatewayImpl.registerType(bpmnModelBuilder);
    GlobalConversationImpl.registerType(bpmnModelBuilder);
    GroupImpl.registerType(bpmnModelBuilder);
    HumanPerformerImpl.registerType(bpmnModelBuilder);
    ImportImpl.registerType(bpmnModelBuilder);
    InclusiveGatewayImpl.registerType(bpmnModelBuilder);
    Incoming.registerType(bpmnModelBuilder);
    InMessageRef.registerType(bpmnModelBuilder);
    InnerParticipantRef.registerType(bpmnModelBuilder);
    InputDataItemImpl.registerType(bpmnModelBuilder);
    InputSetImpl.registerType(bpmnModelBuilder);
    InputSetRefs.registerType(bpmnModelBuilder);
    InteractionNodeImpl.registerType(bpmnModelBuilder);
    InterfaceImpl.registerType(bpmnModelBuilder);
    InterfaceRef.registerType(bpmnModelBuilder);
    IntermediateCatchEventImpl.registerType(bpmnModelBuilder);
    IntermediateThrowEventImpl.registerType(bpmnModelBuilder);
    IoBindingImpl.registerType(bpmnModelBuilder);
    IoSpecificationImpl.registerType(bpmnModelBuilder);
    ItemAwareElementImpl.registerType(bpmnModelBuilder);
    ItemDefinitionImpl.registerType(bpmnModelBuilder);
    LaneImpl.registerType(bpmnModelBuilder);
    LaneSetImpl.registerType(bpmnModelBuilder);
    LinkEventDefinitionImpl.registerType(bpmnModelBuilder);
    LoopCardinalityImpl.registerType(bpmnModelBuilder);
    LoopCharacteristicsImpl.registerType(bpmnModelBuilder);
    LoopDataInputRef.registerType(bpmnModelBuilder);
    LoopDataOutputRef.registerType(bpmnModelBuilder);
    ManualTaskImpl.registerType(bpmnModelBuilder);
    MessageEventDefinitionImpl.registerType(bpmnModelBuilder);
    MessageFlowAssociationImpl.registerType(bpmnModelBuilder);
    MessageFlowImpl.registerType(bpmnModelBuilder);
    MessageFlowRef.registerType(bpmnModelBuilder);
    MessageImpl.registerType(bpmnModelBuilder);
    MessagePath.registerType(bpmnModelBuilder);
    ModelElementInstanceImpl.registerType(bpmnModelBuilder);
    MonitoringImpl.registerType(bpmnModelBuilder);
    MultiInstanceLoopCharacteristicsImpl.registerType(bpmnModelBuilder);
    OperationImpl.registerType(bpmnModelBuilder);
    OperationRef.registerType(bpmnModelBuilder);
    OptionalInputRefs.registerType(bpmnModelBuilder);
    OptionalOutputRefs.registerType(bpmnModelBuilder);
    OuterParticipantRef.registerType(bpmnModelBuilder);
    OutMessageRef.registerType(bpmnModelBuilder);
    Outgoing.registerType(bpmnModelBuilder);
    OutputDataItemImpl.registerType(bpmnModelBuilder);
    OutputSetImpl.registerType(bpmnModelBuilder);
    OutputSetRefs.registerType(bpmnModelBuilder);
    ParallelGatewayImpl.registerType(bpmnModelBuilder);
    ParticipantAssociationImpl.registerType(bpmnModelBuilder);
    ParticipantImpl.registerType(bpmnModelBuilder);
    ParticipantMultiplicityImpl.registerType(bpmnModelBuilder);
    ParticipantRef.registerType(bpmnModelBuilder);
    PartitionElement.registerType(bpmnModelBuilder);
    PerformerImpl.registerType(bpmnModelBuilder);
    PotentialOwnerImpl.registerType(bpmnModelBuilder);
    ProcessImpl.registerType(bpmnModelBuilder);
    PropertyImpl.registerType(bpmnModelBuilder);
    ReceiveTaskImpl.registerType(bpmnModelBuilder);
    RelationshipImpl.registerType(bpmnModelBuilder);
    RenderingImpl.registerType(bpmnModelBuilder);
    ResourceAssignmentExpressionImpl.registerType(bpmnModelBuilder);
    ResourceImpl.registerType(bpmnModelBuilder);
    ResourceParameterBindingImpl.registerType(bpmnModelBuilder);
    ResourceParameterImpl.registerType(bpmnModelBuilder);
    ResourceRef.registerType(bpmnModelBuilder);
    ResourceRoleImpl.registerType(bpmnModelBuilder);
    RootElementImpl.registerType(bpmnModelBuilder);
    ScriptImpl.registerType(bpmnModelBuilder);
    ScriptTaskImpl.registerType(bpmnModelBuilder);
    SendTaskImpl.registerType(bpmnModelBuilder);
    SequenceFlowImpl.registerType(bpmnModelBuilder);
    ServiceTaskImpl.registerType(bpmnModelBuilder);
    SignalEventDefinitionImpl.registerType(bpmnModelBuilder);
    SignalImpl.registerType(bpmnModelBuilder);
    Source.registerType(bpmnModelBuilder);
    SourceRef.registerType(bpmnModelBuilder);
    StartEventImpl.registerType(bpmnModelBuilder);
    SubConversationImpl.registerType(bpmnModelBuilder);
    SubProcessImpl.registerType(bpmnModelBuilder);
    SupportedInterfaceRef.registerType(bpmnModelBuilder);
    Supports.registerType(bpmnModelBuilder);
    Target.registerType(bpmnModelBuilder);
    TargetRef.registerType(bpmnModelBuilder);
    TaskImpl.registerType(bpmnModelBuilder);
    TerminateEventDefinitionImpl.registerType(bpmnModelBuilder);
    TextImpl.registerType(bpmnModelBuilder);
    TextAnnotationImpl.registerType(bpmnModelBuilder);
    ThrowEventImpl.registerType(bpmnModelBuilder);
    TimeCycleImpl.registerType(bpmnModelBuilder);
    TimeDateImpl.registerType(bpmnModelBuilder);
    TimeDurationImpl.registerType(bpmnModelBuilder);
    TimerEventDefinitionImpl.registerType(bpmnModelBuilder);
    To.registerType(bpmnModelBuilder);
    TransactionImpl.registerType(bpmnModelBuilder);
    Transformation.registerType(bpmnModelBuilder);
    UserTaskImpl.registerType(bpmnModelBuilder);
    WhileExecutingInputRefs.registerType(bpmnModelBuilder);
    WhileExecutingOutputRefs.registerType(bpmnModelBuilder);

    /** DC */
    FontImpl.registerType(bpmnModelBuilder);
    PointImpl.registerType(bpmnModelBuilder);
    BoundsImpl.registerType(bpmnModelBuilder);

    /** DI */
    DiagramImpl.registerType(bpmnModelBuilder);
    DiagramElementImpl.registerType(bpmnModelBuilder);
    EdgeImpl.registerType(bpmnModelBuilder);
    io.camunda.zeebe.model.bpmn.impl.instance.di.ExtensionImpl.registerType(bpmnModelBuilder);
    LabelImpl.registerType(bpmnModelBuilder);
    LabeledEdgeImpl.registerType(bpmnModelBuilder);
    LabeledShapeImpl.registerType(bpmnModelBuilder);
    NodeImpl.registerType(bpmnModelBuilder);
    PlaneImpl.registerType(bpmnModelBuilder);
    ShapeImpl.registerType(bpmnModelBuilder);
    StyleImpl.registerType(bpmnModelBuilder);
    WaypointImpl.registerType(bpmnModelBuilder);

    /** BPMNDI */
    BpmnDiagramImpl.registerType(bpmnModelBuilder);
    BpmnEdgeImpl.registerType(bpmnModelBuilder);
    BpmnLabelImpl.registerType(bpmnModelBuilder);
    BpmnLabelStyleImpl.registerType(bpmnModelBuilder);
    BpmnPlaneImpl.registerType(bpmnModelBuilder);
    BpmnShapeImpl.registerType(bpmnModelBuilder);

    /* Zeebe stuff */
    ZeebeHeaderImpl.registerType(bpmnModelBuilder);
    ZeebeInputImpl.registerType(bpmnModelBuilder);
    ZeebeIoMappingImpl.registerType(bpmnModelBuilder);
    ZeebeOutputImpl.registerType(bpmnModelBuilder);
    ZeebeSubscriptionImpl.registerType(bpmnModelBuilder);
    ZeebeTaskDefinitionImpl.registerType(bpmnModelBuilder);
    ZeebeTaskHeadersImpl.registerType(bpmnModelBuilder);
    ZeebeLoopCharacteristicsImpl.registerType(bpmnModelBuilder);
    ZeebeCalledElementImpl.registerType(bpmnModelBuilder);
    ZeebeFormDefinitionImpl.registerType(bpmnModelBuilder);
    ZeebeUserTaskFormImpl.registerType(bpmnModelBuilder);
    ZeebeAssignmentDefinitionImpl.registerType(bpmnModelBuilder);
    ZeebeTaskScheduleImpl.registerType(bpmnModelBuilder);
    ZeebeCalledDecisionImpl.registerType(bpmnModelBuilder);
    ZeebePropertyImpl.registerType(bpmnModelBuilder);
    ZeebePropertiesImpl.registerType(bpmnModelBuilder);
    ZeebeScriptImpl.registerType(bpmnModelBuilder);
  }

  /**
   * @return the {@link Model} instance to use
   */
  public Model getBpmnModel() {
    return bpmnModel;
  }

  /**
   * @param bpmnModel the bpmnModel to set
   */
  public void setBpmnModel(final Model bpmnModel) {
    this.bpmnModel = bpmnModel;
  }

  public ModelBuilder getBpmnModelBuilder() {
    return bpmnModelBuilder;
  }
}
