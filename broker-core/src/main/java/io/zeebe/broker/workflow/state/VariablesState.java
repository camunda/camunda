/*
 * Zeebe Broker Core
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
package io.zeebe.broker.workflow.state;

import io.zeebe.broker.logstreams.state.ZbColumnFamilies;
import io.zeebe.db.ColumnFamily;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbCompositeKey;
import io.zeebe.db.impl.DbLong;
import io.zeebe.db.impl.DbString;
import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackToken;
import io.zeebe.msgpack.spec.MsgPackWriter;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Int2IntHashMap.EntryIterator;
import org.agrona.collections.ObjectHashSet;
import org.agrona.concurrent.UnsafeBuffer;

public class VariablesState {

  public static final int NO_PARENT = -1;

  private final MsgPackReader reader = new MsgPackReader();
  private final MsgPackWriter writer = new MsgPackWriter();
  private final ExpandableArrayBuffer documentResultBuffer = new ExpandableArrayBuffer();
  private final DirectBuffer resultView = new UnsafeBuffer(0, 0);

  // (child scope key) => (parent scope key)
  private final ColumnFamily<DbLong, DbLong> childParentColumnFamily;
  private final DbLong parentKey;
  private final DbLong childKey;

  // (scope key, variable name) => (variable value)
  private final ColumnFamily<DbCompositeKey<DbLong, DbString>, Variable> variablesColumnFamily;
  private final DbCompositeKey<DbLong, DbString> scopeKeyVariableNameKey;
  private final DbLong scopeKey;
  private final DbString variableName;
  private final Variable variable;
  private final DirectBuffer variableNameView = new UnsafeBuffer(0, 0);

  // collecting variables
  private final ObjectHashSet<DirectBuffer> collectedVariables = new ObjectHashSet<>();
  private final ObjectHashSet<DirectBuffer> variablesToCollect = new ObjectHashSet<>();;

  // setting variables
  private IndexedDocument indexedDocument = new IndexedDocument();

  public VariablesState(ZeebeDb<ZbColumnFamilies> zeebeDb) {
    parentKey = new DbLong();
    childKey = new DbLong();
    childParentColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ELEMENT_INSTANCE_CHILD_PARENT, childKey, parentKey);

    scopeKey = new DbLong();
    variableName = new DbString();
    scopeKeyVariableNameKey = new DbCompositeKey<>(scopeKey, variableName);
    variable = new Variable();
    variablesColumnFamily =
        zeebeDb.createColumnFamily(ZbColumnFamilies.VARIABLES, scopeKeyVariableNameKey, variable);
  }

  public void setVariablesLocalFromDocument(long scopeKey, DirectBuffer document) {
    reader.wrap(document, 0, document.capacity());

    final int variables = reader.readMapHeader();

    for (int i = 0; i < variables; i++) {
      final MsgPackToken variableName = reader.readToken();
      final int nameLength = variableName.getValueBuffer().capacity();
      final int nameOffset = reader.getOffset() - nameLength;

      final int valueOffset = reader.getOffset();
      reader.skipValue();
      final int valueLength = reader.getOffset() - valueOffset;

      setVariableLocal(
          scopeKey, document, nameOffset, nameLength, document, valueOffset, valueLength);
    }
  }

  public void setVariableLocal(long scopeKey, DirectBuffer name, DirectBuffer value) {
    setVariableLocal(scopeKey, name, 0, name.capacity(), value, 0, value.capacity());
  }

  private void setVariableLocal(
      long scopeKey,
      DirectBuffer name,
      int nameOffset,
      int nameLength,
      DirectBuffer value,
      int valueOffset,
      int valueLength) {
    this.scopeKey.wrapLong(scopeKey);
    variableNameView.wrap(name, nameOffset, nameLength);
    this.variableName.wrapBuffer(variableNameView);

    variable.wrapValue(value, valueOffset, valueLength);
    variablesColumnFamily.put(scopeKeyVariableNameKey, variable);
  }

  private boolean hasVariableLocal(
      long scopeKey, DirectBuffer name, int nameOffset, int nameLength) {
    this.scopeKey.wrapLong(scopeKey);
    variableNameView.wrap(name, nameOffset, nameLength);
    this.variableName.wrapBuffer(variableNameView);

    return variablesColumnFamily.exists(scopeKeyVariableNameKey);
  }

  public DirectBuffer getVariableLocal(long scopeKey, DirectBuffer name) {
    this.scopeKey.wrapLong(scopeKey);
    this.variableName.wrapBuffer(name);

    final Variable variable = variablesColumnFamily.get(scopeKeyVariableNameKey);
    return variable == null ? null : variable.getValue();
  }

  public void setVariablesFromDocument(long scopeKey, DirectBuffer document) {
    // 1. index entries in the document
    indexedDocument.index(document);

    long currentScope = scopeKey;
    long parentScope;

    // 2. overwrite any variables in the scope hierarchy
    while (indexedDocument.hasEntries() && (parentScope = getParent(currentScope)) > 0) {
      final DocumentEntryIterator entryIterator = indexedDocument.iterator();

      while (entryIterator.hasNext()) {
        entryIterator.next();

        final boolean hasVariable =
            hasVariableLocal(
                currentScope,
                document,
                entryIterator.getNameOffset(),
                entryIterator.getNameLength());

        if (hasVariable) {
          setVariableLocal(
              currentScope,
              document,
              entryIterator.getNameOffset(),
              entryIterator.getNameLength(),
              document,
              entryIterator.getValueOffset(),
              entryIterator.getValueLength());

          entryIterator.remove();
        }
      }
      currentScope = parentScope;
    }

    // 3. set remaining variables on top scope
    if (indexedDocument.hasEntries()) {
      final DocumentEntryIterator entryIterator = indexedDocument.iterator();

      while (entryIterator.hasNext()) {
        entryIterator.next();

        setVariableLocal(
            currentScope,
            document,
            entryIterator.getNameOffset(),
            entryIterator.getNameLength(),
            document,
            entryIterator.getValueOffset(),
            entryIterator.getValueLength());
      }
    }
  }

  private long getParent(long childKey) {
    this.childKey.wrapLong(childKey);

    final DbLong parentKey = childParentColumnFamily.get(this.childKey);
    return parentKey != null ? parentKey.getValue() : NO_PARENT;
  }

  public DirectBuffer getVariablesAsDocument(long scopeKey) {

    collectedVariables.clear();
    writer.wrap(documentResultBuffer, 0);

    writer.reserveMapHeader();

    visitVariables(
        scopeKey,
        name -> !collectedVariables.contains(name.getBuffer()),
        (name, value) -> {
          final DirectBuffer variableNameBuffer = name.getBuffer();
          writer.writeString(variableNameBuffer);
          writer.writeRaw(value.getValue());

          // must create a new name wrapper, because we keep them all in the hashset at the same
          // time
          final MutableDirectBuffer nameView = new UnsafeBuffer(variableNameBuffer);
          collectedVariables.add(nameView);
        },
        () -> false);

    writer.writeReservedMapHeader(0, collectedVariables.size());

    resultView.wrap(documentResultBuffer, 0, writer.getOffset());
    return resultView;
  }

  public DirectBuffer getVariablesAsDocument(long scopeKey, Collection<DirectBuffer> names) {

    variablesToCollect.clear();
    variablesToCollect.addAll(names);

    writer.wrap(documentResultBuffer, 0);

    writer.reserveMapHeader();

    visitVariables(
        scopeKey,
        name -> variablesToCollect.contains(name.getBuffer()),
        (name, value) -> {
          writer.writeString(name.getBuffer());
          writer.writeRaw(value.getValue());

          variablesToCollect.remove(name.getBuffer());
        },
        () -> variablesToCollect.isEmpty());

    writer.writeReservedMapHeader(0, names.size() - variablesToCollect.size());

    resultView.wrap(documentResultBuffer, 0, writer.getOffset());
    return resultView;
  }

  /**
   * Like {@link #visitVariablesLocal(long, Predicate, BiConsumer, BooleanSupplier)} but walks up
   * the scope hierarchy.
   */
  private void visitVariables(
      long scopeKey,
      Predicate<DbString> filter,
      BiConsumer<DbString, Variable> variableConsumer,
      BooleanSupplier completionCondition) {
    long currentScope = scopeKey;

    boolean completed;
    do {
      completed = visitVariablesLocal(currentScope, filter, variableConsumer, completionCondition);

      currentScope = getParent(currentScope);

    } while (!completed && currentScope >= 0);
  }

  /**
   * Provides all variables of a scope to the given consumer until a condition is met.
   *
   * @param scopeKey
   * @param variableFilter evaluated with the name of each variable; the variable is consumed only
   *     if the filter returns true
   * @param variableConsumer a consumer that receives variable name and value
   * @param completionCondition evaluated after every consumption; if true, consumption stops.
   * @return true if the completion condition was met
   */
  private boolean visitVariablesLocal(
      long scopeKey,
      Predicate<DbString> variableFilter,
      BiConsumer<DbString, Variable> variableConsumer,
      BooleanSupplier completionCondition) {
    this.scopeKey.wrapLong(scopeKey);

    variablesColumnFamily.whileEqualPrefix(
        this.scopeKey,
        (compositeKey, variable) -> {
          final DbString variableName = compositeKey.getSecond();

          if (variableFilter.test(variableName)) {
            variableConsumer.accept(variableName, variable);
          }

          return !completionCondition.getAsBoolean();
        });
    return false;
  }

  public void createScope(long childKey, long parentKey) {
    this.childKey.wrapLong(childKey);
    this.parentKey.wrapLong(parentKey);

    childParentColumnFamily.put(this.childKey, this.parentKey);
  }

  public void removeScope(long scopeKey) {
    this.scopeKey.wrapLong(scopeKey);

    removeAllVariables(scopeKey);

    childParentColumnFamily.delete(this.scopeKey);
  }

  public void removeAllVariables(long scopeKey) {
    visitVariablesLocal(
        scopeKey,
        dbString -> true,
        (dbString, variable1) -> variablesColumnFamily.delete(scopeKeyVariableNameKey),
        () -> false);
  }

  public boolean isEmpty() {
    return variablesColumnFamily.isEmpty() && childParentColumnFamily.isEmpty();
  }

  private class IndexedDocument implements Iterable<Void> {
    // variable name offset -> variable value offset
    private Int2IntHashMap entries = new Int2IntHashMap(-1);
    private DocumentEntryIterator iterator = new DocumentEntryIterator();
    private DirectBuffer document;

    public void index(DirectBuffer document) {
      this.document = document;
      entries.clear();
      final int documentLength = document.capacity();

      reader.wrap(document, 0, documentLength);

      final int variables = reader.readMapHeader();

      for (int i = 0; i < variables; i++) {
        final int keyOffset = reader.getOffset();
        reader.skipValue();
        final int valueOffset = reader.getOffset();
        reader.skipValue();

        entries.put(keyOffset, valueOffset);
      }
    }

    @Override
    public DocumentEntryIterator iterator() {
      iterator.wrap(document, entries.entrySet().iterator());
      return iterator;
    }

    public boolean hasEntries() {
      return !entries.isEmpty();
    }
  }

  private class DocumentEntryIterator implements Iterator<Void> {
    private EntryIterator iterator;
    private DirectBuffer document;
    private int documentLength;

    // per entry
    private int nameOffset;
    private int nameLength;
    private int valueOffset;
    private int valueLength;

    @Override
    public boolean hasNext() {
      return iterator.hasNext();
    }

    public void wrap(DirectBuffer document, EntryIterator iterator) {
      this.iterator = iterator;
      this.document = document;
      this.documentLength = document.capacity();
    }

    /** excluding string header */
    public int getNameOffset() {
      return nameOffset;
    }

    public int getNameLength() {
      return nameLength;
    }

    /** including header */
    public int getValueOffset() {
      return valueOffset;
    }

    public int getValueLength() {
      return valueLength;
    }

    @Override
    public Void next() {
      iterator.next();

      final int keyOffset = iterator.getIntKey();
      valueOffset = iterator.getIntValue();

      reader.wrap(document, keyOffset, documentLength - keyOffset);

      nameLength = reader.readStringLength();
      nameOffset = keyOffset + reader.getOffset();

      reader.wrap(document, valueOffset, documentLength - valueOffset);
      reader.skipValue();
      valueLength = reader.getOffset();

      return null;
    }

    @Override
    public void remove() {
      iterator.remove();
    }
  }
}
