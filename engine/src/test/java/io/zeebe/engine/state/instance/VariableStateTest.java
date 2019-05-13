/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.state.instance;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.instance.VariablesState.VariableListener;
import io.zeebe.engine.util.ZeebeStateRule;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.DirectBuffer;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

public class VariableStateTest {

  private static final long WORKFLOW_KEY = 123;
  private static final AtomicLong PARENT_KEY = new AtomicLong(0);
  private static final AtomicLong CHILD_KEY = new AtomicLong(1);
  private static final AtomicLong SECOND_CHILD_KEY = new AtomicLong(2);

  @ClassRule public static ZeebeStateRule stateRule = new ZeebeStateRule();

  private static ElementInstanceState elementInstanceState;
  private static VariablesState variablesState;
  private static RecordingVariableListener listener;

  @BeforeClass
  public static void setUp() {
    final ZeebeState zeebeState = stateRule.getZeebeState();
    elementInstanceState = zeebeState.getWorkflowState().getElementInstanceState();
    variablesState = elementInstanceState.getVariablesState();

    listener = new RecordingVariableListener();
    variablesState.setListener(listener);
  }

  private long parent;
  private long child;
  private long child2;

  @Before
  public void beforeTest() {
    parent = PARENT_KEY.getAndIncrement();
    child = CHILD_KEY.getAndIncrement();
    child2 = SECOND_CHILD_KEY.getAndIncrement();
  }

  @After
  public void cleanUp() {
    elementInstanceState.removeInstance(child2);
    elementInstanceState.removeInstance(child);
    elementInstanceState.removeInstance(parent);
    listener.reset();
  }

  @Test
  public void shouldCollectVariablesAsDocument() {
    // given
    declareScope(parent);

    final DirectBuffer var1Value = MsgPackUtil.asMsgPack("a", 1);
    setVariableLocal(parent, BufferUtil.wrapString("var1"), var1Value);

    final DirectBuffer var2Value = MsgPackUtil.asMsgPack("x", 10);
    setVariableLocal(parent, BufferUtil.wrapString("var2"), var2Value);

    // when
    final DirectBuffer variablesDocument = variablesState.getVariablesAsDocument(parent);

    // then
    MsgPackUtil.assertEquality(variablesDocument, "{'var1': {'a': 1}, 'var2': {'x': 10}}");
  }

  @Test
  public void shouldCollectNoVariablesAsEmptyDocument() {
    // given
    declareScope(parent);

    // when
    final DirectBuffer variablesDocument = variablesState.getVariablesAsDocument(parent);

    // then
    MsgPackUtil.assertEquality(variablesDocument, "{}");
  }

  @Test
  public void shouldCollectVariablesFromMultipleScopes() {
    // given
    final long grandparent = parent;
    final long parent = child;
    final long child = child2;
    declareScope(grandparent);
    declareScope(grandparent, parent);
    declareScope(parent, child);

    setVariableLocal(grandparent, BufferUtil.wrapString("a"), MsgPackUtil.asMsgPack("1"));
    setVariableLocal(parent, BufferUtil.wrapString("b"), MsgPackUtil.asMsgPack("2"));
    setVariableLocal(child, BufferUtil.wrapString("c"), MsgPackUtil.asMsgPack("3"));

    // when
    final DirectBuffer variablesDocument = variablesState.getVariablesAsDocument(child);

    // then
    MsgPackUtil.assertEquality(variablesDocument, "{'a': 1, 'b': 2, 'c': 3}");
  }

  @Test
  public void shouldNotCollectHiddenVariables() {
    // given
    declareScope(parent);
    declareScope(parent, child);

    setVariableLocal(parent, BufferUtil.wrapString("a"), MsgPackUtil.asMsgPack("1"));
    setVariableLocal(parent, BufferUtil.wrapString("b"), MsgPackUtil.asMsgPack("2"));
    setVariableLocal(child, BufferUtil.wrapString("b"), MsgPackUtil.asMsgPack("3"));

    // when
    final DirectBuffer variablesDocument = variablesState.getVariablesAsDocument(child);

    // then
    MsgPackUtil.assertEquality(variablesDocument, "{'a': 1, 'b': 3}");
  }

  @Test
  public void shouldNotCollectVariablesFromChildScope() {
    // given
    declareScope(parent);
    declareScope(parent, child);

    setVariableLocal(parent, BufferUtil.wrapString("a"), MsgPackUtil.asMsgPack("1"));
    setVariableLocal(child, BufferUtil.wrapString("b"), MsgPackUtil.asMsgPack("3"));

    // when
    final DirectBuffer variablesDocument = variablesState.getVariablesAsDocument(parent);

    // then
    MsgPackUtil.assertEquality(variablesDocument, "{'a': 1}");
  }

  @Test
  public void shouldNotCollectVariablesInSiblingScope() {
    // given
    declareScope(parent);
    declareScope(parent, child);
    declareScope(parent, child2);

    setVariableLocal(parent, BufferUtil.wrapString("a"), MsgPackUtil.asMsgPack("1"));
    setVariableLocal(child, BufferUtil.wrapString("b"), MsgPackUtil.asMsgPack("2"));
    setVariableLocal(child2, BufferUtil.wrapString("c"), MsgPackUtil.asMsgPack("3"));

    // when
    final DirectBuffer variablesDocument = variablesState.getVariablesAsDocument(child);

    // then
    MsgPackUtil.assertEquality(variablesDocument, "{'a': 1, 'b': 2}");
  }

  @Test
  public void shouldCollectLocalVariables() {
    // given
    declareScope(parent);
    declareScope(parent, child);

    setVariableLocal(parent, BufferUtil.wrapString("a"), MsgPackUtil.asMsgPack("1"));
    setVariableLocal(child, BufferUtil.wrapString("b"), MsgPackUtil.asMsgPack("3"));

    // then
    MsgPackUtil.assertEquality(variablesState.getVariablesLocalAsDocument(parent), "{'a': 1}");
    MsgPackUtil.assertEquality(variablesState.getVariablesLocalAsDocument(child), "{'b': 3}");
  }

  @Test
  public void shouldCollectVariablesByName() {
    // given
    declareScope(parent);

    setVariableLocal(parent, BufferUtil.wrapString("a"), MsgPackUtil.asMsgPack("1"));
    setVariableLocal(parent, BufferUtil.wrapString("b"), MsgPackUtil.asMsgPack("2"));
    setVariableLocal(parent, BufferUtil.wrapString("c"), MsgPackUtil.asMsgPack("3"));

    // when
    final DirectBuffer variablesDocument =
        variablesState.getVariablesAsDocument(
            parent, Arrays.asList(BufferUtil.wrapString("a"), BufferUtil.wrapString("c")));

    // then
    MsgPackUtil.assertEquality(variablesDocument, "{'a': 1, 'c': 3}");
  }

  @Test
  public void shouldCollectVariablesByNameFromMultipleScopes() {
    // given
    declareScope(parent);
    declareScope(parent, child);
    declareScope(child, child2);

    setVariableLocal(parent, BufferUtil.wrapString("a"), MsgPackUtil.asMsgPack("1"));
    setVariableLocal(child, BufferUtil.wrapString("b"), MsgPackUtil.asMsgPack("2"));
    setVariableLocal(child2, BufferUtil.wrapString("c"), MsgPackUtil.asMsgPack("3"));

    // when
    final DirectBuffer variablesDocument =
        variablesState.getVariablesAsDocument(
            child2, Arrays.asList(BufferUtil.wrapString("a"), BufferUtil.wrapString("c")));

    // then
    MsgPackUtil.assertEquality(variablesDocument, "{'a': 1, 'c': 3}");
  }

  @Test
  public void shouldCollectOnlyExistingVariablesByName() {
    // given
    declareScope(parent);

    setVariableLocal(parent, BufferUtil.wrapString("a"), MsgPackUtil.asMsgPack("1"));

    // when
    final DirectBuffer variablesDocument =
        variablesState.getVariablesAsDocument(
            parent, Arrays.asList(BufferUtil.wrapString("a"), BufferUtil.wrapString("c")));

    // then
    MsgPackUtil.assertEquality(variablesDocument, "{'a': 1}");
  }

  @Test
  public void shouldSetLocalVariablesFromDocument() {
    // given
    declareScope(parent);

    final DirectBuffer document = MsgPackUtil.asMsgPack(b -> b.put("a", 1).put("b", 2));

    // when
    setVariablesLocalFromDocument(parent, document);

    // then
    final DirectBuffer varA = variablesState.getVariableLocal(parent, BufferUtil.wrapString("a"));
    MsgPackUtil.assertEquality(varA, "1");

    final DirectBuffer varB = variablesState.getVariableLocal(parent, BufferUtil.wrapString("b"));
    MsgPackUtil.assertEquality(varB, "2");
  }

  @Test
  public void shouldSetLocalVariablesFromDocumentInHierarchy() {
    // given
    declareScope(parent);
    declareScope(parent, child);

    final DirectBuffer document = MsgPackUtil.asMsgPack(b -> b.put("a", 1).put("b", 2));

    // when
    setVariablesLocalFromDocument(child, document);

    // then
    final DirectBuffer varA = variablesState.getVariableLocal(child, BufferUtil.wrapString("a"));
    MsgPackUtil.assertEquality(varA, "1");
    Assertions.assertThat(variablesState.getVariableLocal(parent, BufferUtil.wrapString("a")))
        .isNull();

    final DirectBuffer varB = variablesState.getVariableLocal(child, BufferUtil.wrapString("b"));
    MsgPackUtil.assertEquality(varB, "2");
    Assertions.assertThat(variablesState.getVariableLocal(parent, BufferUtil.wrapString("b")))
        .isNull();
  }

  @Test
  public void shouldSetLocalVariableFromDocumentAsObject() {
    // given
    declareScope(parent);

    final DirectBuffer document =
        MsgPackUtil.asMsgPack(b -> b.put("var", Collections.singletonMap("a", 1)));

    // when
    setVariablesLocalFromDocument(parent, document);

    // then
    final DirectBuffer varA = variablesState.getVariableLocal(parent, BufferUtil.wrapString("var"));
    MsgPackUtil.assertEquality(varA, "{'a': 1}");
  }

  @Test
  public void shouldOverwriteLocalVariableFromDocument() {
    // given
    declareScope(parent);

    setVariableLocal(parent, BufferUtil.wrapString("a"), MsgPackUtil.asMsgPack("1"));

    final DirectBuffer document = MsgPackUtil.asMsgPack("a", 2);

    // when
    setVariablesLocalFromDocument(parent, document);

    // then
    final DirectBuffer varA = variablesState.getVariableLocal(parent, BufferUtil.wrapString("a"));
    MsgPackUtil.assertEquality(varA, "2");
  }

  @Test
  public void shouldGetNullForNonExistingVariable() {
    // given
    declareScope(parent);

    // when
    final DirectBuffer variableValue =
        variablesState.getVariableLocal(parent, BufferUtil.wrapString("a"));

    // then
    assertThat(variableValue).isNull();
  }

  @Test
  public void shouldRemoveAllVariablesForScope() {
    // given
    declareScope(parent);
    declareScope(parent, child);

    setVariableLocal(parent, BufferUtil.wrapString("parentVar1"), MsgPackUtil.asMsgPack("1"));
    setVariableLocal(child, BufferUtil.wrapString("childVar1"), MsgPackUtil.asMsgPack("2"));
    setVariableLocal(child, BufferUtil.wrapString("childVar2"), MsgPackUtil.asMsgPack("3"));

    // when
    variablesState.removeAllVariables(child);

    // then
    final DirectBuffer document = variablesState.getVariablesAsDocument(child);

    MsgPackUtil.assertEquality(document, "{'parentVar1': 1}");
  }

  @Test
  public void shouldRemoveScope() {
    // given
    declareScope(parent);
    declareScope(parent, child);

    setVariableLocal(parent, BufferUtil.wrapString("parentVar1"), MsgPackUtil.asMsgPack("1"));
    setVariableLocal(child, BufferUtil.wrapString("childVar1"), MsgPackUtil.asMsgPack("2"));
    setVariableLocal(child, BufferUtil.wrapString("childVar2"), MsgPackUtil.asMsgPack("3"));

    // when
    variablesState.removeScope(child);

    // then
    final DirectBuffer document = variablesState.getVariablesAsDocument(child);

    MsgPackUtil.assertEquality(document, "{}");
  }

  @Test
  public void shouldSetVariablesFromDocument() {
    // given
    final long grandparent = parent;
    final long parent = child;
    final long child = child2;
    declareScope(grandparent);
    declareScope(grandparent, parent);
    declareScope(parent, child);

    setVariableLocal(
        grandparent, BufferUtil.wrapString("a"), MsgPackUtil.asMsgPack("'should-overwrite-this'"));
    setVariableLocal(
        parent, BufferUtil.wrapString("b"), MsgPackUtil.asMsgPack("'should-overwrite-this'"));
    setVariableLocal(
        child, BufferUtil.wrapString("c"), MsgPackUtil.asMsgPack("'should-overwrite-this'"));

    final DirectBuffer document =
        MsgPackUtil.asMsgPack(b -> b.put("a", 1).put("b", 2).put("c", 3).put("d", 4));

    // when
    setVariablesFromDocument(child, document);

    // then
    final DirectBuffer varA =
        variablesState.getVariableLocal(grandparent, BufferUtil.wrapString("a"));
    MsgPackUtil.assertEquality(varA, "1");

    final DirectBuffer varB = variablesState.getVariableLocal(parent, BufferUtil.wrapString("b"));
    MsgPackUtil.assertEquality(varB, "2");

    final DirectBuffer varC = variablesState.getVariableLocal(child, BufferUtil.wrapString("c"));
    MsgPackUtil.assertEquality(varC, "3");

    final DirectBuffer varD =
        variablesState.getVariableLocal(grandparent, BufferUtil.wrapString("d"));
    MsgPackUtil.assertEquality(varD, "4");
  }

  @Test
  public void shouldSetVariablesFromDocumentNotInChildScopes() {
    // given
    declareScope(parent);
    declareScope(parent, child);

    setVariableLocal(
        child, BufferUtil.wrapString("b"), MsgPackUtil.asMsgPack("'should-not-overwrite-this'"));

    final DirectBuffer document = MsgPackUtil.asMsgPack(b -> b.put("a", 1).put("b", 2));

    // when
    setVariablesFromDocument(parent, document);

    // then
    final DirectBuffer varA = variablesState.getVariableLocal(parent, BufferUtil.wrapString("a"));
    MsgPackUtil.assertEquality(varA, "1");

    final DirectBuffer varBParent =
        variablesState.getVariableLocal(parent, BufferUtil.wrapString("b"));
    MsgPackUtil.assertEquality(varBParent, "2");

    final DirectBuffer varBChild =
        variablesState.getVariableLocal(child, BufferUtil.wrapString("b"));
    MsgPackUtil.assertEquality(varBChild, "'should-not-overwrite-this'");
  }

  @Test
  public void shouldSetVariablesFromDocumentAsObject() {
    // given
    declareScope(parent);

    final DirectBuffer document =
        MsgPackUtil.asMsgPack(b -> b.put("a", Collections.singletonMap("x", 1)));

    // when
    setVariablesFromDocument(parent, document);

    // then
    final DirectBuffer varA = variablesState.getVariableLocal(parent, BufferUtil.wrapString("a"));
    MsgPackUtil.assertEquality(varA, "{'x': 1}");
  }

  @Test
  public void shouldSetVariablesFromDocumentNotInParentScope() {
    // given
    declareScope(parent);
    declareScope(parent, child);

    setVariableLocal(
        parent, BufferUtil.wrapString("a"), MsgPackUtil.asMsgPack("'should-not-overwrite-this'"));
    setVariableLocal(
        child, BufferUtil.wrapString("a"), MsgPackUtil.asMsgPack("'should-overwrite-this'"));

    final DirectBuffer document = MsgPackUtil.asMsgPack(b -> b.put("a", 1).put("b", 2));

    // when
    setVariablesFromDocument(child, document);

    // then
    final DirectBuffer varParent =
        variablesState.getVariableLocal(parent, BufferUtil.wrapString("a"));
    MsgPackUtil.assertEquality(varParent, "'should-not-overwrite-this'");

    final DirectBuffer newVarParent =
        variablesState.getVariableLocal(parent, BufferUtil.wrapString("b"));
    MsgPackUtil.assertEquality(newVarParent, "2");

    final DirectBuffer varChild =
        variablesState.getVariableLocal(child, BufferUtil.wrapString("a"));
    MsgPackUtil.assertEquality(varChild, "1");
  }

  /** Making sure the method is reusable and does not leave data structures dirty */
  @Test
  public void shouldSetVariablesFromDocumentRepeatedly() {
    // given
    final long parent1 = parent;
    final long parent2 = child;
    declareScope(parent1);
    declareScope(parent2);

    setVariablesFromDocument(parent1, MsgPackUtil.asMsgPack("{'a': 1, 'b': 2}"));

    // when
    setVariablesFromDocument(parent2, MsgPackUtil.asMsgPack("{'x': 3}"));

    // then
    final DirectBuffer parent1Doc = variablesState.getVariablesAsDocument(parent1);
    MsgPackUtil.assertEquality(parent1Doc, "{'a': 1, 'b': 2}");

    final DirectBuffer parent2Doc = variablesState.getVariablesAsDocument(parent2);
    MsgPackUtil.assertEquality(parent2Doc, "{'x': 3}");
  }

  @Test
  public void shouldInvokeListenerOnCreate() {
    // given

    // when
    setVariableLocal(parent, wrapString("x"), wrapString("foo"));

    // then
    assertThat(listener.created).hasSize(1);
    assertThat(listener.created.get(0).name).isEqualTo("x");
    assertThat(listener.created.get(0).value).isEqualTo("foo".getBytes());
    assertThat(listener.created.get(0).variableScopeKey).isEqualTo(parent);
    assertThat(listener.created.get(0).rootScopeKey).isEqualTo(parent);

    assertThat(listener.updated).isEmpty();
  }

  @Test
  public void shouldInvokeListenerOnUpdate() {
    // given

    // when
    setVariableLocal(parent, wrapString("x"), wrapString("foo"));
    setVariableLocal(parent, wrapString("x"), wrapString("bar"));

    // then
    assertThat(listener.created).hasSize(1);

    assertThat(listener.updated).hasSize(1);
    assertThat(listener.updated.get(0).name).isEqualTo("x");
    assertThat(listener.updated.get(0).value).isEqualTo("bar".getBytes());
    assertThat(listener.updated.get(0).variableScopeKey).isEqualTo(parent);
    assertThat(listener.updated.get(0).rootScopeKey).isEqualTo(parent);
  }

  @Test
  public void shouldNotInvokeListenerIfNotChanged() {
    // given

    // when
    setVariableLocal(parent, wrapString("x"), wrapString("foo"));
    setVariableLocal(parent, wrapString("x"), wrapString("foo"));

    // then
    assertThat(listener.created).hasSize(1);

    assertThat(listener.updated).hasSize(0);
  }

  @Test
  public void shouldInvokeListenerIfSetVariablesLocalFromDocument() {
    // given

    // when
    setVariablesLocalFromDocument(parent, MsgPackUtil.asMsgPack("{'x':'foo', 'y':'bar'}"));

    // then
    assertThat(listener.created).hasSize(2);
    assertThat(listener.created.get(0).name).isEqualTo("x");
    assertThat(listener.created.get(0).value).isEqualTo(stringToMsgpack("foo"));
    assertThat(listener.created.get(0).variableScopeKey).isEqualTo(parent);
    assertThat(listener.created.get(0).rootScopeKey).isEqualTo(parent);

    assertThat(listener.created.get(1).name).isEqualTo("y");
    assertThat(listener.created.get(1).value).isEqualTo(stringToMsgpack("bar"));
    assertThat(listener.created.get(1).variableScopeKey).isEqualTo(parent);
    assertThat(listener.created.get(0).rootScopeKey).isEqualTo(parent);

    assertThat(listener.updated).isEmpty();
  }

  @Test
  public void shouldInvokeListenerIfSetVariablesFromDocument() {
    // given
    final long parentScope = parent;
    final long childScope = child;

    declareScope(parentScope);
    declareScope(parentScope, childScope);

    setVariablesLocalFromDocument(childScope, MsgPackUtil.asMsgPack("{'x':'foo'}"));

    // when
    setVariablesFromDocument(childScope, MsgPackUtil.asMsgPack("{'x':'bar', 'y':'bar'}"));

    // then
    assertThat(listener.created).hasSize(2);
    assertThat(listener.created.get(1).name).isEqualTo("y");
    assertThat(listener.created.get(1).value).isEqualTo(stringToMsgpack("bar"));
    assertThat(listener.created.get(1).variableScopeKey).isEqualTo(parentScope);
    assertThat(listener.created.get(1).rootScopeKey).isEqualTo(parentScope);

    assertThat(listener.updated).hasSize(1);
    assertThat(listener.updated.get(0).name).isEqualTo("x");
    assertThat(listener.updated.get(0).value).isEqualTo(stringToMsgpack("bar"));
    assertThat(listener.updated.get(0).variableScopeKey).isEqualTo(childScope);
    assertThat(listener.updated.get(0).rootScopeKey).isEqualTo(parentScope);
  }

  @Test
  public void shouldSetTemporaryVariables() {
    // when
    variablesState.setTemporaryVariables(parent, wrapString("a"));
    variablesState.setTemporaryVariables(child, wrapString("b"));

    // then
    Assertions.assertThat(variablesState.getTemporaryVariables(parent)).isEqualTo(wrapString("a"));
    Assertions.assertThat(variablesState.getTemporaryVariables(child)).isEqualTo(wrapString("b"));
  }

  @Test
  public void shouldRemoveTemporaryVariables() {
    // given
    variablesState.setTemporaryVariables(parent, wrapString("a"));
    variablesState.setTemporaryVariables(child, wrapString("b"));

    // when
    variablesState.removeTemporaryVariables(parent);

    // then
    Assertions.assertThat(variablesState.getTemporaryVariables(parent)).isNull();
    Assertions.assertThat(variablesState.getTemporaryVariables(child)).isEqualTo(wrapString("b"));
  }

  @Test
  public void shouldReuseVariableKeyOnUpdate() {
    // given

    // when
    setVariableLocal(parent, wrapString("x"), wrapString("foo"));
    setVariableLocal(parent, wrapString("x"), wrapString("bar"));

    // then
    final long variableKey = listener.created.get(0).key;
    assertThat(variableKey).isGreaterThan(0);
    assertThat(listener.updated.get(0).key).isEqualTo(variableKey);
  }

  private byte[] stringToMsgpack(String value) {
    return MsgPackUtil.encodeMsgPack(b -> b.packString(value)).byteArray();
  }

  private void declareScope(long key) {
    declareScope(-1, key);
  }

  private void declareScope(long parentKey, long key) {
    final ElementInstance parent = elementInstanceState.getInstance(parentKey);

    final TypedRecord<WorkflowInstanceRecord> record = mockTypedRecord(key, parentKey);
    elementInstanceState.newInstance(
        parent, key, record.getValue(), WorkflowInstanceIntent.ELEMENT_ACTIVATING);
  }

  private TypedRecord<WorkflowInstanceRecord> mockTypedRecord(long key, long parentKey) {
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord(parentKey);

    final TypedRecord<WorkflowInstanceRecord> typedRecord = mock(TypedRecord.class);
    when(typedRecord.getKey()).thenReturn(key);
    when(typedRecord.getValue()).thenReturn(workflowInstanceRecord);
    final RecordMetadata metadata = new RecordMetadata();
    metadata.intent(WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    when(typedRecord.getMetadata()).thenReturn(metadata);

    return typedRecord;
  }

  private WorkflowInstanceRecord createWorkflowInstanceRecord(long parentKey) {
    final WorkflowInstanceRecord workflowInstanceRecord = new WorkflowInstanceRecord();

    if (parentKey >= 0) {
      workflowInstanceRecord.setFlowScopeKey(parentKey);
    }

    return workflowInstanceRecord;
  }

  private static class RecordingVariableListener implements VariableListener {

    private class VariableChange {
      private final long key;
      private final String name;
      private final byte[] value;
      private final long variableScopeKey;
      private final long rootScopeKey;

      VariableChange(
          long key, String name, byte[] value, long variableScopeKey, long rootScopeKey) {
        this.key = key;
        this.name = name;
        this.value = value;
        this.variableScopeKey = variableScopeKey;
        this.rootScopeKey = rootScopeKey;
      }
    }

    private final List<VariableChange> created = new ArrayList<>();
    private final List<VariableChange> updated = new ArrayList<>();

    @Override
    public void onCreate(
        long key,
        long workflowKey,
        DirectBuffer name,
        DirectBuffer value,
        long variableScopeKey,
        long rootScopeKey) {
      final VariableChange change =
          new VariableChange(
              key,
              bufferAsString(name),
              BufferUtil.bufferAsArray(value),
              variableScopeKey,
              rootScopeKey);
      created.add(change);
    }

    @Override
    public void onUpdate(
        long key,
        long workflowKey,
        DirectBuffer name,
        DirectBuffer value,
        long variableScopeKey,
        long rootScopeKey) {
      final VariableChange change =
          new VariableChange(
              key,
              bufferAsString(name),
              BufferUtil.bufferAsArray(value),
              variableScopeKey,
              rootScopeKey);
      updated.add(change);
    }

    public void reset() {
      updated.clear();
      created.clear();
    }
  }

  private void setVariablesFromDocument(long scope, DirectBuffer document) {
    variablesState.setVariablesFromDocument(scope, WORKFLOW_KEY, document);
  }

  private void setVariablesLocalFromDocument(long scope, DirectBuffer document) {
    variablesState.setVariablesLocalFromDocument(scope, WORKFLOW_KEY, document);
  }

  public void setVariableLocal(long scopeKey, DirectBuffer name, DirectBuffer value) {
    variablesState.setVariableLocal(
        scopeKey, WORKFLOW_KEY, name, 0, name.capacity(), value, 0, value.capacity());
  }
}
