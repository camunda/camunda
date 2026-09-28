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
package io.camunda.process.test.api;

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.process.test.api.judge.JudgeConfig;
import io.camunda.process.test.api.judge.WeightedExpectation;
import io.camunda.process.test.impl.judge.jev.JevChatModelAdapter;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Runs the real "AI Agent Chat With Tools" quickstart
 * (https://github.com/camunda/camunda-8-tutorials/tree/main/solutions/ai-agent-chat-with-tools)
 * through a real Camunda engine with Connectors enabled, wired to a real Anthropic model on AWS
 * Bedrock via the v2 AI Agent Subprocess connector template, and lets the agent call its tools
 * (real remote APIs) for real. The final agent response is then judged by the real Jev (TypeSafe)
 * API for a meaningful, content-based assertion.
 *
 * <p>{@code shouldFailAgentResponseQualityExpectationsOnARegressedResponse} and {@code
 * shouldFailConversationConsistencyExpectationsOnAMismatchedRecipe} reuse the same expectations
 * against canned bad fixtures, without calling Bedrock or Connectors, to show the expectations
 * double as a fast, deterministic regression check. Both also rely on {@link
 * JudgeConfig#withPenaltyExponent(double)} (see {@code PENALTY_EXPONENT}) so that one badly-failing
 * criterion reliably sinks the weighted average instead of being outvoted by unrelated passing
 * ones.
 *
 * <p>Skipped unless all three real credentials are available. Run manually with:
 *
 * <pre>
 * TYPESAFE_API_KEY=&lt;key&gt; \
 * PERSONAL_AWS_BEDROCK_ACCESS_KEY=&lt;key&gt; \
 * PERSONAL_AWS_BEDROCK_SECRET_KEY=&lt;secret&gt; \
 * ./mvnw verify -pl testing/camunda-process-test-java \
 *     -Dit.test=AiAgentChatWithToolsIT -DskipTests=false -DskipUTs
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PERSONAL_AWS_BEDROCK_ACCESS_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PERSONAL_AWS_BEDROCK_SECRET_KEY", matches = ".+")
public class AiAgentChatWithToolsIT {

  private static final String DEFAULT_BEDROCK_REGION = "us-east-1";

  // squaring truth values before the weighted average is combined means a single badly-failing,
  // correctness-critical criterion can't be diluted away by several unrelated passing ones - see
  // JudgeConfig#withPenaltyExponent for the general rationale, and
  // shouldFailConversationConsistencyExpectationsOnAMismatchedRecipe for a worked example
  private static final double PENALTY_EXPONENT = 2.0;

  @RegisterExtension
  private static final CamundaProcessTestExtension EXTENSION =
      new CamundaProcessTestExtension()
          .withConnectorsEnabled(true)
          .withConnectorsSecret(
              "PERSONAL_AWS_BEDROCK_ACCESS_KEY", System.getenv("PERSONAL_AWS_BEDROCK_ACCESS_KEY"))
          .withConnectorsSecret(
              "PERSONAL_AWS_BEDROCK_SECRET_KEY", System.getenv("PERSONAL_AWS_BEDROCK_SECRET_KEY"))
          .withConnectorsSecret(
              "PERSONAL_AWS_BEDROCK_REGION",
              System.getenv().getOrDefault("PERSONAL_AWS_BEDROCK_REGION", DEFAULT_BEDROCK_REGION));

  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;

  @AfterEach
  void resetJudgeConfig() {
    CamundaAssert.setJudgeConfig(null);
    CamundaAssert.setAssertionTimeout(CamundaAssert.DEFAULT_ASSERTION_TIMEOUT);
  }

  @Test
  void shouldRunRealAiAgentAndJudgeItsFinalResponseWithJev() {
    // given - the real agent call (Bedrock + tool round-trip) takes much longer than the
    // 10s default assertion timeout
    CamundaAssert.setAssertionTimeout(Duration.ofMinutes(2));
    CamundaAssert.setJudgeConfig(
        JudgeConfig.of(new JevChatModelAdapter(System.getenv("TYPESAFE_API_KEY")))
            .withPenaltyExponent(PENALTY_EXPONENT));

    client
        .newDeployResourceCommand()
        .addResourceFromClasspath("ai-agent-chat-with-tools/ai-agent-chat-with-tools.bpmn")
        .addResourceFromClasspath("ai-agent-chat-with-tools/ai-agent-chat-initial-request.form")
        .addResourceFromClasspath("ai-agent-chat-with-tools/ai-agent-chat-user-feedback.form")
        .send()
        .join();

    // when - the real agent should call the real recipe-search tool for this request
    final ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("ai-agent-chat-with-tools")
            .latestVersion()
            .variable(
                "inputText",
                "Search for a chicken recipe and briefly tell me its name and one ingredient.")
            .send()
            .join();

    // then - the agent's response, handed to the user as a local variable on User_Feedback, is
    // judged against several independent, weighted criteria in a single Jev batch call. A plain
    // judge only returns one free-text yes/no verdict, which forces every concern into one blurry
    // score; Jev's batch classifier scores each axis separately (grounding, task completion,
    // brevity, tone, non-disclosure of internals) so a regression on one axis can't hide behind a
    // strong score on another. Squaring each truth value (PENALTY_EXPONENT, see below) before
    // averaging means weight alone isn't enough for several passing axes to outvote one that
    // fails badly - a healthy response barely notices (0.97 -> 0.94), but see
    // shouldFailConversationConsistencyExpectationsOnAMismatchedRecipe for what it catches.
    assertThatProcessInstance(processInstance)
        .hasLocalVariableSatisfiesJudge(
            "User_Feedback", "responseText", agentResponseQualityExpectations());

    // and - the "agent" process variable carries the whole conversation (via
    // includeAgentContext=true on the connector template): every assistant message, its
    // toolCalls, and the matching tool_call_result content. Judging responseText alone can only
    // ever ask "does this sound plausible?" — a fluent hallucination of a different, equally
    // real-sounding dish would pass it. Judging the whole conversation lets Jev cross-reference
    // the claim against the ground truth that's sitting right there in the same payload.
    assertThatProcessInstance(processInstance)
        .hasVariableSatisfiesJudge("agent", agentConversationConsistencyExpectations());

    // and - complete the loop like a satisfied user would
    final long userTaskKey = findUserFeedbackTaskKey(processInstance);
    client
        .newCompleteUserTaskCommand(userTaskKey)
        .variables(Collections.singletonMap("userSatisfied", true))
        .send()
        .join();

    assertThatProcessInstance(processInstance).isCompleted();
  }

  @Test
  void shouldFailAgentResponseQualityExpectationsOnARegressedResponse() {
    // given - a canned response standing in for a regressed agent: it leaks tool-call internals
    // and falsely claims the search failed, instead of naming a real recipe. It doesn't call
    // Bedrock or Connectors at all, which is the point: once the expectations exist, replaying
    // them against recorded "bad" outputs is a fast, deterministic regression check that doesn't
    // depend on the real agent producing the same failure again.
    CamundaAssert.setJudgeConfig(
        JudgeConfig.of(new JevChatModelAdapter(System.getenv("TYPESAFE_API_KEY")))
            .withPenaltyExponent(PENALTY_EXPONENT));

    final String regressedResponseText =
        "I called the Search_Recipe tool with query 'chicken' and got "
            + "{\"recipes\": []} as the JSON result. Sorry, the recipe search tool failed, "
            + "so I couldn't find anything for you.";

    final String processId = "regressed-ai-agent-response";
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();
    client.newDeployResourceCommand().addProcessModel(process, processId + ".bpmn").send().join();

    final ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .variable("responseText", regressedResponseText)
            .send()
            .join();

    // then - the same expectations that pass for a healthy response catch the regression: no
    // recipe name/ingredient (grounding), tool/JSON internals leaked (no-internals-leak), and a
    // false failure claim (no-false-refusal) all score low, dragging the weighted average below
    // the threshold even though the response is admittedly brief
    assertThatThrownBy(
            () ->
                assertThatProcessInstance(processInstance)
                    .hasVariableSatisfiesJudge("responseText", agentResponseQualityExpectations()))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("did not satisfy expectations")
        .hasMessageContaining("Weighted score");
  }

  @Test
  void shouldFailConversationConsistencyExpectationsOnAMismatchedRecipe() {
    // given - a canned "agent" conversation standing in for a regressed agent: the tool call
    // actually returned "Chicken Vesuvio" with "chicken thighs", but the final response text
    // names an unrelated, equally plausible-sounding dish ("Butter Chicken" / "paneer"). A judge
    // that only ever saw responseText would call this a perfectly good answer - it reads like a
    // real recipe. Only cross-checking against the tool_call_result in the same conversation
    // exposes the mismatch.
    CamundaAssert.setJudgeConfig(
        JudgeConfig.of(new JevChatModelAdapter(System.getenv("TYPESAFE_API_KEY")))
            .withPenaltyExponent(PENALTY_EXPONENT));

    // built as nested Map/List rather than hand-typed JSON text so the client's own serializer
    // guarantees valid JSON (in particular the doubly-escaped tool_call_result.content.text,
    // which carries the tool's raw JSON output as a string)
    final String toolResultJson =
        "{\"recipes\": [{\"name\": \"Chicken Vesuvio\", "
            + "\"ingredients\": [\"chicken thighs\", \"potatoes\", \"peas\"]}]}";
    final Map<String, Object> toolCall =
        mapOf("id", "tc1", "name", "Search_Recipe", "arguments", mapOf("searchQuery", "chicken"));
    final Map<String, Object> assistantMessage =
        mapOf("role", "assistant", "toolCalls", Arrays.asList(toolCall));

    final Map<String, Object> toolResultContent = mapOf("type", "text", "text", toolResultJson);
    final Map<String, Object> toolCallResult =
        mapOf(
            "id", "tc1",
            "name", "Search_Recipe",
            "content", Arrays.asList(toolResultContent));
    final Map<String, Object> toolCallResultMessage =
        mapOf("role", "tool_call_result", "results", Arrays.asList(toolCallResult));

    final Map<String, Object> conversation =
        mapOf("messages", Arrays.asList(assistantMessage, toolCallResultMessage));
    final Map<String, Object> context = mapOf("conversation", conversation);
    final Map<String, Object> mismatchedAgentConversation =
        mapOf(
            "responseText",
            "Great news! I found a Butter Chicken recipe for you. "
                + "It's made with paneer as one of the key ingredients.",
            "context",
            context);

    final String processId = "regressed-ai-agent-conversation";
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();
    client.newDeployResourceCommand().addProcessModel(process, processId + ".bpmn").send().join();

    final ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .variable("agent", mismatchedAgentConversation)
            .send()
            .join();

    // then - the grounding cross-check catches the mismatch even though the response text alone
    // reads as a fluent, on-topic answer. Verified against the real Jev API: it scores grounding
    // at ~0.01 (correctly caught) while the other three axes score ~0.97/0.90/0.65 (correctly
    // true, since the agent DID pick the right tool with a sane query and didn't retry). Under a
    // PLAIN weighted average, their combined weight (2.0 + 1.5 + 1.5 = 5.0) alone clears the
    // default 0.5 threshold (0.5 * 8.0 = 4.0) even with a 0 on the 3.0-weight grounding axis -
    // three correct axes mathematically outvoting one catastrophic one. PENALTY_EXPONENT fixes
    // this at the source rather than papering over it with a stricter threshold: squaring each
    // truth value first crushes the near-zero grounding score (0.01 -> ~0.0001) while barely
    // touching the passing ones (0.97 -> 0.94), so the weighted average collapses well below 0.5
    // without any per-assertion threshold tuning.
    assertThatThrownBy(
            () ->
                assertThatProcessInstance(processInstance)
                    .hasVariableSatisfiesJudge("agent", agentConversationConsistencyExpectations()))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("did not satisfy expectations")
        .hasMessageContaining("Weighted score");
  }

  private static List<WeightedExpectation> agentConversationConsistencyExpectations() {
    // Judges the whole "agent" conversation (assistant messages, their toolCalls, and the
    // matching tool_call_result content), not just the final response text. This is where a
    // classifier judge earns its keep over a plain string assertion: "does the claimed recipe
    // match what the tool actually returned" isn't expressible as a regex or equals() check once
    // wording can differ, but it's a natural-language criterion Jev can evaluate directly against
    // the raw JSON blob.
    return Arrays.asList(
        // critical: this is the actual hallucination check - the claim in responseText must be
        // traceable to the Search_Recipe tool_call_result, not merely plausible on its own
        WeightedExpectation.of(
            "the recipe name and ingredient mentioned in responseText match the name and "
                + "ingredients returned in the Search_Recipe tool_call_result content, rather "
                + "than naming a different dish or ingredient",
            3.0),
        // important: the agent picked the right tool for the job and didn't go fishing
        WeightedExpectation.of(
            "the conversation shows a tool call named Search_Recipe with a chicken-related "
                + "search query, and does not call unrelated tools such as ListUsers, "
                + "Jokes_API, or Get list of Tech Stuff",
            2.0),
        // important: catches a retry-storm or looping regression that would still yield a fine
        // final response, so it's invisible to responseText-only checks
        WeightedExpectation.of(
            "Search_Recipe is called at most once for this request, with no repeated or "
                + "retried tool calls for the same search query",
            1.5),
        // nice-to-have: the tool was actually given a sensible query, not an empty or garbled one
        WeightedExpectation.of(
            "the search query argument passed to Search_Recipe reflects the user's actual "
                + "request rather than being empty or unrelated to chicken recipes",
            1.5));
  }

  private static List<WeightedExpectation> agentResponseQualityExpectations() {
    // Several independent, weighted criteria evaluated in a single Jev batch call. A plain judge
    // only returns one free-text yes/no verdict, which forces every concern into one blurry
    // score; Jev's batch classifier scores each axis separately (grounding, task completion,
    // brevity, tone, non-disclosure of internals) so a regression on one axis can't hide behind a
    // strong score on another, and weights let critical axes (did it actually use the real tool
    // result?) dominate over cosmetic ones (is the tone nice?).
    return Arrays.asList(
        // critical: the response must be grounded in the real Search_Recipe tool result
        // (dummyjson.com), not a plausible-sounding but fabricated dish/ingredient
        WeightedExpectation.of(
            "names a specific chicken recipe and at least one of its ingredients, "
                + "consistent with a real recipe database entry rather than an invented "
                + "or generic dish",
            3.0),
        // critical: answers what was actually asked, nothing more, nothing less
        WeightedExpectation.of(
            "directly answers the user's request for the recipe's name and one "
                + "ingredient, without ignoring either part of the request",
            3.0),
        // important: a judge that just checks "mentions a recipe" would miss a response
        // that also lists the full ingredient list, cooking steps, or nutrition facts
        WeightedExpectation.of(
            "is brief (a sentence or two), not a full recipe dump with an ingredient "
                + "list, instructions, or nutritional information",
            2.0),
        // important: internals of the agent loop (tool names, JSON, API URLs, "calling a
        // tool now") must not leak into the user-facing text
        WeightedExpectation.of(
            "contains no mention of tool names, function calls, JSON, or API/technical "
                + "implementation details",
            2.0),
        // important: the agent must not hedge or refuse when the tool call succeeded
        WeightedExpectation.of(
            "does not refuse, apologize for being unable to help, or claim the recipe "
                + "search failed",
            2.0),
        // nice-to-have: tone polish, lowest weight since it's cosmetic
        WeightedExpectation.of("reads as a helpful, conversational reply", 1.0));
  }

  /**
   * Builds a {@code LinkedHashMap} from alternating key/value pairs, for readable JSON fixtures.
   */
  private static Map<String, Object> mapOf(final Object... keysAndValues) {
    final Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      map.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return map;
  }

  private long findUserFeedbackTaskKey(final ProcessInstanceEvent processInstance) {
    final UserTask userTask =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(200))
            .until(
                () ->
                    client
                        .newUserTaskSearchRequest()
                        .filter(
                            filter ->
                                filter
                                    .processInstanceKey(processInstance.getProcessInstanceKey())
                                    .elementId("User_Feedback")
                                    .state(UserTaskState.CREATED))
                        .send()
                        .join()
                        .items()
                        .stream()
                        .findFirst()
                        .orElse(null),
                userTaskOrNull -> userTaskOrNull != null);
    return userTask.getUserTaskKey();
  }
}
