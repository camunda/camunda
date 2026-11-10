/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;

/**
 * Applies the same random sequence of operations to two {@link ZeebeDb} implementations and
 * requires that every operation has the same observable outcome (return value, or the type of the
 * exception thrown) and that both end in the same state.
 *
 * <p>The keys come from a small range so that operations collide often. This is what exercises the
 * insert-on-existing, update-on-missing and delete-on-missing paths, and the rollback of batches.
 */
@Tag("randomized")
public abstract class DifferentialRandomizedContractTest {

  private static final int KEY_RANGE = 8;

  /** The implementation that defines the expected behavior. */
  protected abstract ZeebeDbFactory<DefaultColumnFamily> createReferenceFactory();

  /** The implementation that must behave like the reference. */
  protected abstract ZeebeDbFactory<DefaultColumnFamily> createCandidateFactory();

  private Instance reference;
  private Instance candidate;

  @BeforeTry
  void setup() throws IOException {
    reference = new Instance(createReferenceFactory());
    candidate = new Instance(createCandidateFactory());
  }

  @AfterTry
  void teardown() throws Exception {
    reference.close();
    candidate.close();
  }

  @Property(tries = 300)
  void shouldBehaveTheSameForRandomOperations(
      @ForAll("operations") final List<Operation> operations) {
    for (final var operation : operations) {
      assertThat(operation.applyTo(candidate))
          .as("outcome of %s", operation)
          .isEqualTo(operation.applyTo(reference));
    }

    assertThat(new Scan().applyTo(candidate))
        .as("final state")
        .isEqualTo(new Scan().applyTo(reference));
  }

  @Provide
  Arbitrary<List<Operation>> operations() {
    final var key = Arbitraries.longs().between(0, KEY_RANGE - 1);
    final var value = Arbitraries.longs();

    final Arbitrary<Operation> single =
        Arbitraries.oneOf(
            key.flatMap(k -> value.map(v -> new Insert(k, v))),
            key.flatMap(k -> value.map(v -> new Update(k, v))),
            key.flatMap(k -> value.map(v -> new Upsert(k, v))),
            key.map(DeleteExisting::new),
            key.map(DeleteIfExists::new),
            key.map(Get::new),
            key.map(Exists::new),
            key.map(ScanFrom::new),
            key.map(ScanReverseFrom::new),
            Arbitraries.just(new Scan()),
            Arbitraries.just(new Count()));

    final Arbitrary<Operation> batch =
        single
            .list()
            .ofMaxSize(5)
            .flatMap(ops -> Arbitraries.of(true, false).map(abort -> new Batch(ops, abort)));

    return Arbitraries.frequencyOf(
            net.jqwik.api.Tuple.of(8, single), net.jqwik.api.Tuple.of(2, batch))
        .list()
        .ofMaxSize(60);
  }

  private static final class Instance implements AutoCloseable {
    private final File directory;
    private final ZeebeDb<DefaultColumnFamily> db;
    private final TransactionContext context;
    private final ColumnFamily<DbLong, DbLong> columnFamily;

    private Instance(final ZeebeDbFactory<DefaultColumnFamily> factory) throws IOException {
      directory = Files.createTempDirectory("zb-db-differential").toFile();
      db = factory.createDb(directory);
      context = db.createContext();
      columnFamily =
          db.createColumnFamily(DefaultColumnFamily.DEFAULT, context, new DbLong(), new DbLong());
    }

    @Override
    public void close() throws Exception {
      db.close();
      org.assertj.core.util.Files.delete(directory);
    }
  }

  /** Thrown at the end of an aborted batch to force the transaction to roll back. */
  private static final class AbortBatch extends RuntimeException {}

  sealed interface Operation {
    /** Returns a comparable description of what the operation returned, or the exception type. */
    default Object applyTo(final Instance instance) {
      try {
        return execute(instance);
      } catch (final AbortBatch e) {
        throw e;
      } catch (final RuntimeException e) {
        return e.getClass().getName();
      }
    }

    Object execute(Instance instance);
  }

  private static DbLong dbLong(final long value) {
    final var dbLong = new DbLong();
    dbLong.wrapLong(value);
    return dbLong;
  }

  record Insert(long key, long value) implements Operation {
    @Override
    public Object execute(final Instance i) {
      i.columnFamily.insert(dbLong(key), dbLong(value));
      return "ok";
    }
  }

  record Update(long key, long value) implements Operation {
    @Override
    public Object execute(final Instance i) {
      i.columnFamily.update(dbLong(key), dbLong(value));
      return "ok";
    }
  }

  record Upsert(long key, long value) implements Operation {
    @Override
    public Object execute(final Instance i) {
      i.columnFamily.upsert(dbLong(key), dbLong(value));
      return "ok";
    }
  }

  record DeleteExisting(long key) implements Operation {
    @Override
    public Object execute(final Instance i) {
      i.columnFamily.deleteExisting(dbLong(key));
      return "ok";
    }
  }

  record DeleteIfExists(long key) implements Operation {
    @Override
    public Object execute(final Instance i) {
      i.columnFamily.deleteIfExists(dbLong(key));
      return "ok";
    }
  }

  record Get(long key) implements Operation {
    @Override
    public Object execute(final Instance i) {
      final var value = i.columnFamily.get(dbLong(key));
      return value == null ? "null" : value.getValue();
    }
  }

  record Exists(long key) implements Operation {
    @Override
    public Object execute(final Instance i) {
      return i.columnFamily.exists(dbLong(key));
    }
  }

  record Count() implements Operation {
    @Override
    public Object execute(final Instance i) {
      return List.of(i.columnFamily.count(), i.columnFamily.isEmpty());
    }
  }

  record Scan() implements Operation {
    @Override
    public Object execute(final Instance i) {
      final var entries = new ArrayList<List<Long>>();
      i.columnFamily.forEach((k, v) -> entries.add(List.of(k.getValue(), v.getValue())));
      return entries;
    }
  }

  record ScanFrom(long startKey) implements Operation {
    @Override
    public Object execute(final Instance i) {
      final var entries = new ArrayList<List<Long>>();
      i.columnFamily.whileTrue(
          dbLong(startKey),
          (k, v) -> {
            entries.add(List.of(k.getValue(), v.getValue()));
            return true;
          });
      return entries;
    }
  }

  record ScanReverseFrom(long startKey) implements Operation {
    @Override
    public Object execute(final Instance i) {
      final var entries = new ArrayList<List<Long>>();
      i.columnFamily.whileTrueReverse(
          dbLong(startKey),
          (k, v) -> {
            entries.add(List.of(k.getValue(), v.getValue()));
            return true;
          });
      return entries;
    }
  }

  /**
   * Runs the operations in one transaction. The first failing operation rolls the whole batch back,
   * and so does {@code abort}. The outcome includes the results of the operations that ran.
   */
  record Batch(List<Operation> operations, boolean abort) implements Operation {
    @Override
    public Object applyTo(final Instance i) {
      final var results = new ArrayList<>();
      try {
        i.context.runInTransaction(
            () -> {
              for (final var operation : operations) {
                results.add(operation.execute(i));
              }
              if (abort) {
                throw new AbortBatch();
              }
            });
        results.add("committed");
      } catch (final AbortBatch e) {
        results.add("aborted");
      } catch (final RuntimeException e) {
        results.add(e.getClass().getName());
      }
      return results;
    }

    @Override
    public Object execute(final Instance i) {
      return applyTo(i);
    }
  }
}
