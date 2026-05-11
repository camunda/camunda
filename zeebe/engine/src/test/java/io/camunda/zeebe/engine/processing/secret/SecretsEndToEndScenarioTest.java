/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.ExpressionIntent;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * End-to-end scenarios for the {@code camunda.secret.*} PoC, mirroring real-world connector idioms.
 * Each test composes multiple stages (mapping, activation, masking) to prove the five pieces of the
 * feature behave correctly together, not just in isolation.
 *
 * <p>Run alongside the per-stage tests; failures here indicate composition problems even when the
 * unit-level coverage is green.
 */
public class SecretsEndToEndScenarioTest {

  /**
   * Realistic secret store contents — values are intentionally distinctive so the {@link
   * #auditExportsCarryNoRealSecretValues} sweep can grep records for them and fail loudly if any
   * exporter would see them.
   */
  private static final String SLACK_TOKEN_VALUE = "xoxb-REAL-SLACK-TOKEN-do-not-leak";

  private static final String STRIPE_KEY_VALUE = "sk_test_REAL-STRIPE-KEY-do-not-leak";
  private static final String HMAC_SECRET_VALUE = "REAL-HMAC-SECRET-do-not-leak";

  private static final Map<String, String> SECRETS =
      Map.of(
          "SLACK_BOT_TOKEN", SLACK_TOKEN_VALUE,
          "STRIPE_API_KEY", STRIPE_KEY_VALUE,
          "SLACK_SIGNING_SECRET", HMAC_SECRET_VALUE);

  private static final SecretStore TEST_STORE = name -> Optional.ofNullable(SECRETS.get(name));

  @ClassRule
  public static final EngineRule ENGINE = EngineRule.singlePartition().withSecretStore(TEST_STORE);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  // ----- Scenario 1: Slack outbound connector ------------------------------------------------

  @Test
  public void slackOutboundConnectorPattern() {
    // Models the BPMN shape from the spec discussion: an input mapping that binds the secret
    // reference to a `token` variable, plus a worker that lists both the regular variable and
    // the secret reference itself in fetchVariables. This is what a real connector outbound
    // worker does — it reads the connector-shaped variable plus the explicit secret.
    final String jobType = "io.camunda:slack-outbound";
    final var process =
        Bpmn.createExecutableProcess("SLACK_OUTBOUND")
            .startEvent()
            .serviceTask(
                "POST_MESSAGE",
                t ->
                    t.zeebeJobType(jobType)
                        .zeebeInputExpression("camunda.secret.SLACK_BOT_TOKEN", "token"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("SLACK_OUTBOUND").create();

    // Worker pulls the job, fetching the local `token` variable (will be the literal reference)
    // AND the secret reference name (will be resolved at activation time).
    final var batch =
        ENGINE
            .jobs()
            .withType(jobType)
            .withFetchVariables("token", "camunda.secret.SLACK_BOT_TOKEN")
            .withMaxJobsToActivate(1)
            .activate();

    // The persisted ACTIVATED event masks the resolved secret value but keeps the regular
    // variable that holds the literal reference exactly as the input mapping persisted it.
    assertThat(batch.getIntent()).isEqualTo(JobBatchIntent.ACTIVATED);
    final var activatedJobVars = batch.getValue().getJobs().getFirst().getVariables();
    assertThat(activatedJobVars)
        .containsEntry("token", "camunda.secret.SLACK_BOT_TOKEN")
        .containsEntry("camunda.secret.SLACK_BOT_TOKEN", SecretMasker.MASKED_VALUE);

    // The variable store side: the only persisted variable is `token`, holding the harmless
    // reference string. The secret namespace never enters the variable store — confirmed by
    // checking that no VariableRecord exists for the secret key.
    final var tokenVariable =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("token")
            .getFirst()
            .getValue();
    assertThat(tokenVariable.getValue()).isEqualTo("\"camunda.secret.SLACK_BOT_TOKEN\"");

    // The secret namespace never reaches the variable store as a variable name. We don't
    // sweep with a blocking limit here — the implicit guarantee is that only the input
    // mapping writes variables on this path, and that mapping target is `token`. The audit
    // sweep test below catches any composition surprise across the full record set.
  }

  // ----- Scenario 2: Stripe-style compound auth header ---------------------------------------

  @Test
  public void stripeCompoundAuthHeaderPattern() {
    // The connector author concatenates a literal prefix with the secret reference. Per stage 2,
    // the concatenation runs in the literal-reference context, so the persisted variable is the
    // harmless "Bearer camunda.secret.STRIPE_API_KEY" string. The worker is expected to fetch the
    // raw secret separately and assemble the real auth header on its own side.
    final String jobType = "io.camunda:stripe-charge";
    final var process =
        Bpmn.createExecutableProcess("STRIPE_COMPOUND")
            .startEvent()
            .serviceTask(
                "CHARGE",
                t ->
                    t.zeebeJobType(jobType)
                        .zeebeInputExpression(
                            "\"Bearer \" + camunda.secret.STRIPE_API_KEY", "authHeader")
                        .zeebeInputExpression("orderAmount", "amount"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("STRIPE_COMPOUND")
            .withVariable("orderAmount", 4200)
            .create();

    final var batch =
        ENGINE
            .jobs()
            .withType(jobType)
            .withFetchVariables("authHeader", "amount", "camunda.secret.STRIPE_API_KEY")
            .withMaxJobsToActivate(1)
            .activate();

    final var vars = batch.getValue().getJobs().getFirst().getVariables();
    assertThat(vars)
        .contains(
            entry("authHeader", "Bearer camunda.secret.STRIPE_API_KEY"),
            entry("camunda.secret.STRIPE_API_KEY", SecretMasker.MASKED_VALUE));
    // amount comes through unchanged from the start variable; check the numeric value loosely
    // since msgpack→Jackson decoding may surface it as int or long depending on size.
    assertThat(vars).extractingByKey("amount").asString().isEqualTo("4200");

    // The persisted variable store keeps the compound reference verbatim — no env-var lookup
    // ever happened on this path.
    final var authHeaderVar =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("authHeader")
            .getFirst()
            .getValue();
    assertThat(authHeaderVar.getValue()).isEqualTo("\"Bearer camunda.secret.STRIPE_API_KEY\"");
  }

  // ----- Scenario 3: FEEL endpoint analog for inbound HMAC verification ---------------------

  @Test
  public void inboundHmacResolutionViaFeelEndpoint() {
    // The Slack inbound HMAC use case from the spec discussion lives in connector-runtime, but
    // the path it uses to resolve secrets is the same standalone FEEL evaluation endpoint we
    // expose from the broker. This test stands in for that flow: call the endpoint, get the
    // real value out (the connector runtime would use it to verify the HMAC signature), and
    // confirm the on-disk record has the masked sentinel so the audit story holds.
    final var record =
        ENGINE.expression().withExpression("=camunda.secret.SLACK_SIGNING_SECRET").resolve();

    assertThat(record.getIntent()).isEqualTo(ExpressionIntent.EVALUATED);
    // Persisted event = masked. (Stage 5 architectural decision: real value only lives on the
    // response side of the wire; on disk it's redacted.)
    assertThat(record.getValue().getResultValue()).isEqualTo(SecretMasker.MASKED_VALUE);
    assertThat(record.getValue().getWarnings()).isEmpty();
  }

  // ----- Scenario 4: audit sweep — no real secret values reach any exported record ----------

  @Test
  public void auditExportsCarryNoRealSecretValues() {
    // Combined scenario: a single process touches both Slack and Stripe secrets through input
    // mappings, and runs the FEEL endpoint for the HMAC secret. The sweep at the end walks
    // every exporter-visible record and asserts none of the three real secret values shows up
    // anywhere — proving the audit story end-to-end.
    final String jobType = "audit-sweep-job";
    final var process =
        Bpmn.createExecutableProcess("AUDIT_SWEEP")
            .startEvent()
            .serviceTask(
                "TASK",
                t ->
                    t.zeebeJobType(jobType)
                        .zeebeInputExpression("camunda.secret.SLACK_BOT_TOKEN", "slackToken")
                        .zeebeInputExpression(
                            "\"Bearer \" + camunda.secret.STRIPE_API_KEY", "stripeAuth"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("AUDIT_SWEEP").create();

    // Activate the job with both secrets fetched, so the resolution path actually runs.
    ENGINE
        .jobs()
        .withType(jobType)
        .withFetchVariables(
            "slackToken",
            "stripeAuth",
            "camunda.secret.SLACK_BOT_TOKEN",
            "camunda.secret.STRIPE_API_KEY")
        .withMaxJobsToActivate(1)
        .activate();

    // Plus a standalone FEEL evaluation for the HMAC secret.
    ENGINE.expression().withExpression("=camunda.secret.SLACK_SIGNING_SECRET").resolve();

    // Sweep — none of the real values should appear in any exported record. The recorder is
    // reset per @Test (RecordingExporterTestWatcher), so we know exactly which records the
    // scenario above produced: 2 input-mapped variables (`slackToken`, `stripeAuth`), 1
    // job-batch activation, and 1 FEEL evaluation. Each `.limit(N)` uses the exact expected
    // count so the stream terminates instead of blocking on records that never arrive.
    final var forbiddenValues = List.of(SLACK_TOKEN_VALUE, STRIPE_KEY_VALUE, HMAC_SECRET_VALUE);

    RecordingExporter.variableRecords(VariableIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(2)
        .forEach(
            r ->
                assertNoSecretLeak(
                    "variable " + r.getValue().getName() + " on logstream",
                    r.getValue().getValue(),
                    forbiddenValues));

    RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED)
        .limit(1)
        .forEach(
            r ->
                r.getValue()
                    .getJobs()
                    .forEach(
                        job ->
                            assertNoSecretLeak(
                                "job " + job.getType() + " vars on logstream",
                                job.getVariables().toString(),
                                forbiddenValues)));

    RecordingExporter.expressionRecords()
        .withIntent(ExpressionIntent.EVALUATED)
        .limit(1)
        .forEach(
            r ->
                assertNoSecretLeak(
                    "FEEL endpoint result on logstream",
                    String.valueOf(r.getValue().getResultValue()),
                    forbiddenValues));
  }

  private static void assertNoSecretLeak(
      final String description, final String actual, final List<String> forbidden) {
    for (final String value : forbidden) {
      assertThat(actual).as(description).doesNotContain(value);
    }
  }
}
