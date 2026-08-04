/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.given;

import io.camunda.optimize.AbstractCCSMIT;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import io.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.reader.CollectionReader;
import io.camunda.optimize.service.db.reader.EntitiesReader;
import io.camunda.optimize.service.db.repository.UserIdMigrationRepository;
import io.camunda.optimize.service.db.writer.CollectionWriter;
import io.camunda.optimize.service.db.writer.ReportWriter;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link UserIdMigrationService} / {@link UserIdMigrationRepository}.
 *
 * <p>Scenario: a SaaS user changes their SSO login method. The Accounts API assigns a new {@code
 * user_id}; the old one is stored in {@code app_metadata.originalUserId} and surfaced in the OIDC
 * token. On the next login Optimize detects the claim and migrates all owned entities to the new
 * ID.
 *
 * <p>Collection ownership/roles are asserted via {@link CollectionReader}, not {@link
 * EntitiesReader}: {@code EntitiesReader.getAllPrivateEntitiesForOwnerId} only covers reports and
 * dashboards (it explicitly excludes documents that have a collection ID), never the collection
 * index itself.
 */
public class UserIdMigrationIT extends AbstractCCSMIT {

  private static final String OLD_USER_ID = "old-sso-user-id";
  private static final String NEW_USER_ID = "new-sso-user-id";

  /**
   * Covers three sequential synchronous migration scenarios sharing the OLD_USER_ID/NEW_USER_ID
   * constants: (1) collection owner and embedded role are rewritten and the old ID disappears from
   * all indices; (2) report owner/lastModifier fields are migrated on entity indices that have no
   * nested roles; (3) re-running {@link UserIdMigrationRepository#migrateUserId} after the old ID
   * is already gone is a no-op, since every script is guarded by an {@code == params.oldId} check
   * on the current document state — safe to chain because each scenario only ever migrates
   * documents that are, at that point in time, still tagged with OLD_USER_ID. The count-guard that
   * skips the repository call entirely lives in {@link UserIdMigrationService} and is covered
   * separately by {@link
   * #asyncMigrationViaServiceCompletesBeforeNextLoginCheckAndConcurrentLoginsMigrateOnlyOnce}.
   */
  @Test
  void migratesCollectionOwnershipAndReportOwnerOnUserIdChangeAndRepeatedMigrationIsANoOp() {
    final CollectionWriter collectionWriter =
        embeddedOptimizeExtension.getBean(CollectionWriter.class);
    final CollectionReader collectionReader =
        embeddedOptimizeExtension.getBean(CollectionReader.class);
    final ReportWriter reportWriter = embeddedOptimizeExtension.getBean(ReportWriter.class);
    final EntitiesReader entitiesReader = embeddedOptimizeExtension.getBean(EntitiesReader.class);
    final UserIdMigrationRepository repository =
        embeddedOptimizeExtension.getBean(UserIdMigrationRepository.class);

    // given (collection ownership and roles)
    // the creator is automatically added as a MANAGER role on the collection, so no explicit
    // addRoleToCollection call is needed to have a role entry referencing OLD_USER_ID
    final String collectionId =
        collectionWriter
            .createNewCollectionAndReturnId(
                OLD_USER_ID, new PartialCollectionDefinitionRequestDto("Migration Test"))
            .getId();

    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    CollectionDefinitionDto collection = collectionReader.getCollection(collectionId).orElseThrow();
    assertThat(collection.getOwner()).isEqualTo(OLD_USER_ID);
    assertThat(collection.getData().getRoles())
        .extracting(role -> role.getIdentity().getId())
        .contains(OLD_USER_ID);
    assertThat(repository.hasDocumentsWithUserId(OLD_USER_ID)).isTrue();

    // when (collection ownership and roles)
    repository.migrateUserId(OLD_USER_ID, NEW_USER_ID);
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // then (collection ownership and roles)
    assertThat(repository.hasDocumentsWithUserId(OLD_USER_ID)).isFalse();
    collection = collectionReader.getCollection(collectionId).orElseThrow();
    assertThat(collection.getOwner()).isEqualTo(NEW_USER_ID);
    assertThat(collection.getData().getRoles())
        .extracting(role -> role.getIdentity().getId())
        .contains(NEW_USER_ID)
        .doesNotContain(OLD_USER_ID);

    // given (report owner, no nested roles) — old ID is reintroduced via a fresh entity
    final String reportId =
        reportWriter
            .createNewSingleProcessReport(
                OLD_USER_ID, new ProcessReportDataDto(), "Migration Test Report", null, null)
            .getId();

    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    assertThat(entitiesReader.getAllPrivateEntitiesForOwnerId(OLD_USER_ID))
        .extracting(CollectionEntity::getId)
        .containsExactly(reportId);
    assertThat(repository.hasDocumentsWithUserId(OLD_USER_ID)).isTrue();

    // when (report owner, no nested roles)
    repository.migrateUserId(OLD_USER_ID, NEW_USER_ID);
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // then (report owner, no nested roles)
    assertThat(repository.hasDocumentsWithUserId(OLD_USER_ID)).isFalse();
    assertThat(entitiesReader.getAllPrivateEntitiesForOwnerId(NEW_USER_ID))
        .extracting(CollectionEntity::getId)
        .containsExactly(reportId);
    assertThat(entitiesReader.getAllPrivateEntitiesForOwnerId(OLD_USER_ID)).isEmpty();

    // given (repeated migration of the same user ID is a no-op) — old ID reintroduced again
    final String idempotencyCollectionId =
        collectionWriter
            .createNewCollectionAndReturnId(
                OLD_USER_ID, new PartialCollectionDefinitionRequestDto("Idempotency Test"))
            .getId();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // when (repeated migration of the same user ID is a no-op)
    repository.migrateUserId(OLD_USER_ID, NEW_USER_ID);
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
    assertThat(repository.hasDocumentsWithUserId(OLD_USER_ID)).isFalse();

    // old ID already absent from all indices; scripts find no matching documents and no-op
    repository.migrateUserId(OLD_USER_ID, NEW_USER_ID);
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // then (repeated migration of the same user ID is a no-op)
    assertThat(collectionReader.getCollection(idempotencyCollectionId).orElseThrow().getOwner())
        .isEqualTo(NEW_USER_ID);
  }

  /**
   * Covers two independent scenarios of the async path via {@link UserIdMigrationService}: (1)
   * fire-and-forget resolves before the next login's {@link
   * UserIdMigrationRepository#hasDocumentsWithUserId} check would see the old ID; (2) simulates a
   * logout/login while a migration is still running for the same old user ID — the second {@link
   * UserIdMigrationService#migrateUserIdIfNeeded} call must be a no-op rather than firing a second,
   * redundant {@code _update_by_query} pass. The in-progress flag is set synchronously on the
   * caller's thread before the async task is scheduled, so calling twice back-to-back
   * deterministically exercises the dedup path regardless of execution speed. {@code
   * UserIdMigrationService} is {@code @Conditional(CCSaaSCondition.class)} and therefore not in the
   * Spring context during IT runs; it is instantiated directly in each scenario.
   */
  @Test
  void asyncMigrationViaServiceCompletesBeforeNextLoginCheckAndConcurrentLoginsMigrateOnlyOnce() {
    final CollectionWriter collectionWriter =
        embeddedOptimizeExtension.getBean(CollectionWriter.class);
    final CollectionReader collectionReader =
        embeddedOptimizeExtension.getBean(CollectionReader.class);
    final UserIdMigrationRepository repository =
        embeddedOptimizeExtension.getBean(UserIdMigrationRepository.class);

    // given (async migration completes before next login check)
    final String collectionId =
        collectionWriter
            .createNewCollectionAndReturnId(
                OLD_USER_ID, new PartialCollectionDefinitionRequestDto("Async Migration Test"))
            .getId();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
    assertThat(repository.hasDocumentsWithUserId(OLD_USER_ID)).isTrue();

    // when (async migration completes before next login check)
    final UserIdMigrationService service = new UserIdMigrationService(repository);
    service.migrateUserIdIfNeeded(NEW_USER_ID, OLD_USER_ID);

    // then (async migration completes before next login check)
    given()
        .timeout(30, SECONDS)
        .pollInterval(200, MILLISECONDS)
        .untilAsserted(
            () -> {
              databaseIntegrationTestExtension.refreshAllOptimizeIndices();
              assertThat(repository.hasDocumentsWithUserId(OLD_USER_ID)).isFalse();
              assertThat(collectionReader.getCollection(collectionId).orElseThrow().getOwner())
                  .isEqualTo(NEW_USER_ID);
            });

    // given (concurrent logins for the same old user ID migrate only once) — old ID
    // reintroduced via a fresh entity
    final String dedupCollectionId =
        collectionWriter
            .createNewCollectionAndReturnId(
                OLD_USER_ID, new PartialCollectionDefinitionRequestDto("Dedup Test"))
            .getId();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    final AtomicInteger migrateInvocations = new AtomicInteger();
    final UserIdMigrationRepository countingRepository =
        new UserIdMigrationRepository() {
          @Override
          public boolean hasDocumentsWithUserId(final String userId) {
            return repository.hasDocumentsWithUserId(userId);
          }

          @Override
          public void migrateUserId(final String oldUserId, final String newUserId) {
            migrateInvocations.incrementAndGet();
            repository.migrateUserId(oldUserId, newUserId);
          }
        };

    // when (concurrent logins for the same old user ID migrate only once)
    final UserIdMigrationService dedupService = new UserIdMigrationService(countingRepository);
    dedupService.migrateUserIdIfNeeded(NEW_USER_ID, OLD_USER_ID);
    dedupService.migrateUserIdIfNeeded(NEW_USER_ID, OLD_USER_ID);

    // then (concurrent logins for the same old user ID migrate only once)
    given()
        .timeout(30, SECONDS)
        .pollInterval(200, MILLISECONDS)
        .untilAsserted(
            () -> {
              databaseIntegrationTestExtension.refreshAllOptimizeIndices();
              assertThat(repository.hasDocumentsWithUserId(OLD_USER_ID)).isFalse();
              assertThat(collectionReader.getCollection(dedupCollectionId).orElseThrow().getOwner())
                  .isEqualTo(NEW_USER_ID);
            });

    assertThat(migrateInvocations).hasValue(1);
  }

  /**
   * Reproduces the multi-hop scenario from issue #34982: a user changes their SSO login method
   * twice. As long as each login-triggered migration completes before the next login-method change
   * happens, entities created under every prior identity remain visible under the current one — the
   * entity created as the very first identity, migrated twice (v1→v2→v3), and the entity created as
   * the second identity, migrated once (v2→v3), are both owned by the final ID.
   */
  @Test
  void entitiesFromBothOldIdsAreVisibleAfterTwoSequentialLoginMethodChanges() {
    final CollectionWriter collectionWriter =
        embeddedOptimizeExtension.getBean(CollectionWriter.class);
    final CollectionReader collectionReader =
        embeddedOptimizeExtension.getBean(CollectionReader.class);
    final UserIdMigrationRepository repository =
        embeddedOptimizeExtension.getBean(UserIdMigrationRepository.class);

    final String firstUserId = "sso-user-v1";
    final String secondUserId = "sso-user-v2";
    final String thirdUserId = "sso-user-v3";

    // user creates an entity under their first identity
    final String firstCollectionId =
        collectionWriter
            .createNewCollectionAndReturnId(
                firstUserId, new PartialCollectionDefinitionRequestDto("Created as v1"))
            .getId();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // first SSO login-method change: v1 -> v2, triggered on that login
    repository.migrateUserId(firstUserId, secondUserId);
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
    assertThat(collectionReader.getCollection(firstCollectionId).orElseThrow().getOwner())
        .isEqualTo(secondUserId);

    // user creates a second entity under their new (second) identity
    final String secondCollectionId =
        collectionWriter
            .createNewCollectionAndReturnId(
                secondUserId, new PartialCollectionDefinitionRequestDto("Created as v2"))
            .getId();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // second SSO login-method change: v2 -> v3, triggered on that login
    repository.migrateUserId(secondUserId, thirdUserId);
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // the final identity owns both entities, regardless of which prior identity created them
    assertThat(collectionReader.getCollection(firstCollectionId).orElseThrow().getOwner())
        .isEqualTo(thirdUserId);
    assertThat(collectionReader.getCollection(secondCollectionId).orElseThrow().getOwner())
        .isEqualTo(thirdUserId);
  }
}
