/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.database.DatabaseFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

/**
 * Guards {@code schema-version-seed.xml} against a silent gap: nothing else stops a new 8.9.x patch
 * changeset from being added to {@code changelog-master.xml} without also teaching the seed
 * changelog to recognize a legacy database that finished it.
 *
 * <p>{@code schema-version-seed.xml}'s two seed changesets are hand-written against exactly the
 * 8.9.x changesets that existed when it was authored ({@code 8.9.0.xml} and {@code 8.9.9.xml}):
 * their preconditions fingerprint each changeset's boundary columns/indexes to tell a completed
 * pre-8.10 schema apart from one interrupted mid-migration. A new {@code 8.9.x.xml} changeset
 * inserted before {@code 8.10.0.xml} does not automatically get such a fingerprint — the seed file
 * would keep classifying a legacy database that finished the new changeset as if it had only
 * reached the previous one.
 *
 * <p>This test enumerates the 8.9.x changesets {@code changelog-master.xml} actually applies and
 * compares them against the set the seed file is known to handle. It is a change detector, not a
 * proof of correctness: adding a new 8.9.x changeset fails this test, which is the point — it
 * forces a conscious look at {@code schema-version-seed.xml} (and its own test coverage) before the
 * expected set here is updated to match.
 */
class SchemaVersionSeedCoverageTest {

  private static final String MASTER_CHANGELOG = "db/changelog/rdbms-exporter/changelog-master.xml";

  /** Matches only the pre-8.10, version-tracking-free changesets this seed file is about. */
  private static final Pattern PRE_8_10_CHANGESET_FILE = Pattern.compile("8\\.9\\.\\d+\\.xml");

  /**
   * The 8.9.x changesets {@code schema-version-seed.xml} was written against: {@code 8.9.0.xml}
   * (seeded as the pre-versioning version) and {@code 8.9.9.xml} (seeded once its last column
   * exists). Update this set only after extending {@code schema-version-seed.xml} with a changeset
   * that fingerprints the new file's boundary, and its own tests, to match.
   */
  private static final Set<String> CHANGESETS_HANDLED_BY_SEED = Set.of("8.9.0.xml", "8.9.9.xml");

  @Test
  void shouldHaveASeedChangesetForEveryPre810Changeset() throws Exception {
    // given the changesets changelog-master.xml actually applies before version tracking begins
    final var database = DatabaseFactory.getInstance().getDatabase("h2");
    final var liquibase =
        new Liquibase(MASTER_CHANGELOG, new ClassLoaderResourceAccessor(), database);
    liquibase.setChangeLogParameter("prefix", "");
    liquibase.setChangeLogParameter("userCharColumnSize", 4000);
    liquibase.setChangeLogParameter("errorMessageSize", 4000);
    liquibase.setChangeLogParameter("treePathSize", 4000);

    // when collecting the distinct 8.9.x changeset files it includes
    final var pre810ChangesetFiles = new TreeSet<String>();
    for (final ChangeSet changeSet : liquibase.getDatabaseChangeLog().getChangeSets()) {
      final var fileName = Path.of(changeSet.getFilePath()).getFileName().toString();
      if (PRE_8_10_CHANGESET_FILE.matcher(fileName).matches()) {
        pre810ChangesetFiles.add(fileName);
      }
    }

    // then every one of them must be a changeset schema-version-seed.xml already knows how to
    // recognize; a new file here without a matching update there (and here) is exactly the gap
    // that let #62554 through the first time
    assertThat(pre810ChangesetFiles)
        .describedAs(
            """
            changelog-master.xml applies %s before 8.10.0.xml, but schema-version-seed.xml is only \
            known to handle %s.
            A new 8.9.x changeset needs its own seed changeset in schema-version-seed.xml \
            (fingerprinting the columns/tables/indexes it adds, the way seed_completed_8_9_9_schema_version \
            fingerprints 8.9.9.xml) before CHANGESETS_HANDLED_BY_SEED here is updated to match.""",
            pre810ChangesetFiles, CHANGESETS_HANDLED_BY_SEED)
        .isEqualTo(CHANGESETS_HANDLED_BY_SEED);
  }
}
