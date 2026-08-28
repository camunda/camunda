/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.InputMappings;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.VariableMappingTransformer;
import io.camunda.zeebe.engine.processing.expression.ScopedEvaluationContext;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeMapping;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Runs input-mapping scenarios through both {@link MappingResolver} implementations side by side,
 * so each test documents where {@link OrderedMappingResolver} (the current per-mapping behavior,
 * "Today") and {@link CombinedMappingResolver} (the resurrected pre-{@code baddd911435}
 * combined-FEEL-context behavior, "8.9.0") agree, and precisely why they diverge where they don't.
 *
 * <p>Tests are grouped into {@code @Nested} classes mirroring the eight numbered rules of the
 * internal "Rules that input mappings adhere to" reference doc, one class per rule, with each
 * {@code @Test}'s {@code @DisplayName} citing the doc's own scenario number (e.g. "5.3"). Rule 8
 * (type survival across mappings) describes a real gap in today's behavior, confirmed as
 * camunda/camunda#60011 -- that test intentionally asserts today's actual, non-ideal output, not
 * the proposed fix, and will need deliberate updating once that issue is fixed. Rule 7 (partial
 * shadowing) turned out, once this test exercised a real ancestor-scope hierarchy instead of a flat
 * in-memory stand-in, to already be correctly implemented by {@link OrderedMappingResolver} for
 * every scenario here -- the actual gap sits in {@link CombinedMappingResolver}'s single
 * combined-FEEL-context evaluation, which loses an ancestor's untouched sibling once that object's
 * root has been partially mapped (see 7.3-7.5). Two further groups cover scenarios the doc doesn't
 * address: the qualified/bare-name self-reference cases from issue #60551 that motivated this whole
 * resolver-strategy refactor, and pre-existing transformer-level regression coverage (static-source
 * string preservation, missing source, secret placeholders).
 *
 * <p>No {@code EngineRule}, no BPMN process building: each scenario's mappings are built as raw
 * {@link ZeebeMapping} test doubles (source/target strings exactly as they'd be modeled) and fed
 * through the real {@link VariableMappingTransformer#transformInputMappings} to produce a genuinely
 * realistic {@link InputMappings} (both the per-mapping list and the combined expression),
 * exercising the actual deploy-time code path instead of hand-reconstructing its output.
 *
 * <p>"In scope" data for a scenario is backed by an in-memory {@link ScopedEvaluationContext} built
 * by {@link Helpers#buildProcessor}, which walks the provided scope chain from innermost to
 * outermost -- no embedded database required.
 */
class MappingResolverComparisonTest {

  private static final ExpressionLanguage EXPRESSION_LANGUAGE =
      ExpressionLanguageFactory.createExpressionLanguage(
          new ZeebeFeelEngineClock(InstantSource.system()));
  private static final Duration DEFAULT_TIMEOUT =
      EngineConfiguration.DEFAULT_EXPRESSION_EVALUATION_TIMEOUT;

  private static final OrderedMappingResolver ORDERED = new OrderedMappingResolver();
  private static final CombinedMappingResolver LEGACY = new CombinedMappingResolver();

  @Nested
  @DisplayName("Rule 1: an input mapping creates a local variable")
  class Rule1CreatesALocalVariable {

    @Test
    @DisplayName("1.1 one dotted target creates every level on its path")
    void shouldCreateEveryLevelOnADottedTargetsPath() {
      // given
      final var mappings = List.of(Helpers.mapping("=1", "x.a.b.c"));

      // when
      final var results = Helpers.resolve(mappings, Map.of());

      // then
      Helpers.assertSame(results, "{'x':{'a':{'b':{'c':1}}}}");
    }
  }

  @Nested
  @DisplayName("Rule 2: a source can reference variables from a higher scope")
  class Rule2ReadsFromAHigherScope {

    @Test
    @DisplayName("2.1 reading an ancestor scope's variable")
    void shouldReadAnAncestorScopesVariable() {
      // given
      final var mappings = List.of(Helpers.mapping("=x", "y"));

      // when
      final var results = Helpers.resolve(mappings, Map.of("x", 1));

      // then
      Helpers.assertSame(results, "{'y':1}");
    }

    @Test
    @DisplayName("2.2 reaching into a nested ancestor variable")
    void shouldReachIntoANestedAncestorVariable() {
      // given
      final var mappings =
          List.of(
              Helpers.mapping("=order.id", "id"), Helpers.mapping("=order.customer.name", "who"));
      final Map<String, Object> scope =
          Map.of("order", Map.of("id", 7, "customer", Map.of("name", "Ada")));

      // when
      final var results = Helpers.resolve(mappings, scope);

      // then
      Helpers.assertSame(results, "{'id':7,'who':'Ada'}");
    }
  }

  @Nested
  @DisplayName("Rule 3: an unresolvable source yields null, it does not raise an incident")
  class Rule3UnresolvableSourceYieldsNull {

    @Test
    @DisplayName("3.1 two ways to get null without an incident")
    void shouldResolveUnresolvableSourcesToNull() {
      // given
      final var mappings = List.of(Helpers.mapping("=missing", "x"), Helpers.mapping("=a.b", "y"));

      // when
      final var results = Helpers.resolve(mappings, Map.of("a", 5));

      // then
      Helpers.assertSame(results, "{'x':null,'y':null}");
    }
  }

  @Nested
  @DisplayName("Rule 4: input mappings build the local context")
  class Rule4BuildsTheLocalContext {

    @Test
    @DisplayName("4.1 two mappings build the local context, one variable each")
    void shouldBuildTwoIndependentVariables() {
      // given
      final var mappings = List.of(Helpers.mapping("=1", "a"), Helpers.mapping("=2", "b"));

      // when
      final var results = Helpers.resolve(mappings, Map.of());

      // then
      Helpers.assertSame(results, "{'a':1,'b':2}");
    }

    @Test
    @DisplayName("4.2 two mappings build the same nested object")
    void shouldComposeTwoSiblingTargetsIntoOneObject() {
      // given
      final var mappings = List.of(Helpers.mapping("=1", "x.a"), Helpers.mapping("=2", "x.b"));

      // when
      final var results = Helpers.resolve(mappings, Map.of());

      // then
      Helpers.assertSame(results, "{'x':{'a':1,'b':2}}");
    }
  }

  @Nested
  @DisplayName("Rule 5: input mappings are evaluated in the order they are defined in")
  class Rule5EvaluatedInDeclarationOrder {

    @Test
    @DisplayName("5.1 an earlier mapping's target beats the same name in scope")
    void shouldPreferAnEarlierMappingsTargetOverScope() {
      // given
      final var mappings = List.of(Helpers.mapping("=2", "x"), Helpers.mapping("=x", "y"));

      // when
      final var results = Helpers.resolve(mappings, Map.of("x", 1));

      // then
      Helpers.assertSame(results, "{'x':2,'y':2}");
    }

    @Test
    @DisplayName("5.2 a target assigned twice, with a read in between -- diverges from 8.9.0")
    void shouldReadTheTargetsValueAtTheTimeOfTheRead() {
      // given
      final var mappings =
          List.of(
              Helpers.mapping("=1", "x"), Helpers.mapping("=x", "y"), Helpers.mapping("=2", "x"));

      // when
      final var results = Helpers.resolve(mappings, Map.of());

      // then: ordered evaluates every mapping in order, so y sees x as it stood at mapping 2 (1);
      // legacy's combined FEEL context keeps only the last source per target, so y sees x's final
      // value (2) regardless of where the read appears
      Helpers.assertDiffers(results, "{'x':2,'y':1}", "{'x':2,'y':2}");
    }

    @Test
    @DisplayName("5.3 nested and flat targets interleaved, issue #11789 -- diverges from 8.9.0")
    void shouldForwardAFlatTargetsValueIntoALaterNestedTarget() {
      // given
      final var mappings =
          List.of(
              Helpers.mapping("=1", "obj.first"),
              Helpers.mapping("=2", "flat"),
              Helpers.mapping("=flat", "obj.second"),
              Helpers.mapping("=flat", "flatCopy"));

      // when
      final var results = Helpers.resolve(mappings, Map.of());

      // then: legacy's combined-context builder groups 'obj' before 'flat' in the rendered text
      // regardless of modeling order, so the reference to 'flat' inside 'obj.second' is evaluated
      // before 'flat' is bound and is lost -- this is issue #11789, fixed by evaluating in order
      Helpers.assertDiffers(
          results,
          "{'obj':{'first':1,'second':2},'flat':2,'flatCopy':2}",
          "{'obj':{'first':1,'second':null},'flat':2,'flatCopy':2}");
    }

    @Test
    @DisplayName("5.4 reading a target before the mapping that writes it")
    void shouldFallThroughToScopeForATargetNotYetWritten() {
      // given
      final var mappings = List.of(Helpers.mapping("=z", "a"), Helpers.mapping("=1", "z"));

      // when
      final var results = Helpers.resolve(mappings, Map.of("z", 9));

      // then
      Helpers.assertSame(results, "{'a':9,'z':1}");
    }

    @Test
    @DisplayName("5.5 two mappings referring to each other")
    void shouldDegenerateACycleRatherThanFail() {
      // given
      final var mappings = List.of(Helpers.mapping("=b", "a"), Helpers.mapping("=a", "b"));

      // when
      final var results = Helpers.resolve(mappings, Map.of("a", 1, "b", 2));

      // then
      Helpers.assertSame(results, "{'a':2,'b':2}");
    }

    @Test
    @DisplayName(
        "5.6 nested target from an earlier mapping used as source in a later mapping"
            + " -- both resolvers agree via FEEL context-literal sibling scoping")
    void shouldSeeANestedTargetFromAnEarlierMappingInALaterSource() {
      // given: mapping 1 produces a.b=1; mapping 2 reads a.b as its source
      final var mappings = List.of(Helpers.mapping("=1", "a.b"), Helpers.mapping("=a.b", "c"));

      // when
      final var results = Helpers.resolve(mappings, Map.of());

      // then: ordered sees a.b=1 in the per-mapping accumulator when mapping 2 runs;
      // combined's single FEEL context is {a:{b:1},c:a.b} -- FEEL resolves the bare 'a'
      // in 'a.b' against the context literal's own 'a' entry ({b:1}), so both land on c=1
      Helpers.assertSame(results, "{'a':{'b':1},'c':1}");
    }
  }

  @Nested
  @DisplayName("Rule 6: later input mappings can replace earlier input mappings")
  class Rule6LaterReplacesEarlier {

    @Test
    @DisplayName("6.1 the same target assigned twice")
    void shouldKeepTheLastAssignmentToTheSameTarget() {
      // given
      final var mappings = List.of(Helpers.mapping("=1", "x"), Helpers.mapping("=2", "x"));

      // when
      final var results = Helpers.resolve(mappings, Map.of());

      // then
      Helpers.assertSame(results, "{'x':2}");
    }

    @Test
    @DisplayName("6.2 a scalar replaces an object built earlier")
    void shouldReplaceAnObjectWithALaterScalar() {
      // given
      final var mappings = List.of(Helpers.mapping("=1", "x.a"), Helpers.mapping("=2", "x"));

      // when
      final var results = Helpers.resolve(mappings, Map.of());

      // then
      Helpers.assertSame(results, "{'x':2}");
    }

    @Test
    @DisplayName("6.3 an object replaces a scalar assigned earlier")
    void shouldReplaceAScalarWithALaterObject() {
      // given
      final var mappings = List.of(Helpers.mapping("=1", "x"), Helpers.mapping("=2", "x.a"));

      // when
      final var results = Helpers.resolve(mappings, Map.of());

      // then
      Helpers.assertSame(results, "{'x':{'a':2}}");
    }

    @Test
    @DisplayName("6.4 replacement is scoped to the colliding level, not the whole variable")
    void shouldScopeReplacementToTheCollidingLevelOnly() {
      // given
      final var mappings =
          List.of(
              Helpers.mapping("=1", "x.a"),
              Helpers.mapping("=2", "x.b"),
              Helpers.mapping("=3", "x.a.c"));

      // when
      final var results = Helpers.resolve(mappings, Map.of());

      // then: x.b never collides with x.a.c, so it survives untouched
      Helpers.assertSame(results, "{'x':{'a':{'c':3},'b':2}}");
    }
  }

  @Nested
  @DisplayName(
      "Rule 7: variables from a higher scope are partially shadowed by input mappings"
          + " (OrderedMappingResolver already implements this correctly; the gap is in"
          + " CombinedMappingResolver/8.9.0's combined-FEEL-context evaluation, which loses an"
          + " ancestor's untouched sibling once that object's root has been partially mapped)")
  class Rule7PartiallyShadowsHigherScope {

    @Test
    @DisplayName("7.1 shadowing is total -- even a null hides the ancestor's value")
    void shouldTotallyShadowAnAncestorValueEvenWithNull() {
      // given
      final var mappings = List.of(Helpers.mapping("=null", "x"), Helpers.mapping("=x", "y"));

      // when
      final var results = Helpers.resolve(mappings, Map.of("x", 5));

      // then
      Helpers.assertSame(results, "{'x':null,'y':null}");
    }

    @Test
    @DisplayName("7.2 narrowing an object onto its own name -- both resolvers keep the sibling")
    void shouldKeepUnmappedSiblingsWhenNarrowingAnObjectOntoItself() {
      // given
      final var mappings = List.of(Helpers.mapping("=x.a", "x.a"), Helpers.mapping("=x.b", "x.b"));

      // when
      final var results = Helpers.resolve(mappings, Map.of("x", Map.of("a", 1, "b", 2)));

      // then: once x.a is mapped, reading x mid-evaluation merges the real ancestor's untouched
      // b in through the fallback readback -- both resolvers agree on this shape
      Helpers.assertSame(results, "{'x':{'a':1,'b':2}}");
    }

    @Test
    @DisplayName(
        "7.3 adding a new field to an ancestor object, then reading it back -- diverges from"
            + " 8.9.0")
    void shouldSeeAnAncestorFieldNoMappingTouched() {
      // given
      final var mappings = List.of(Helpers.mapping("=1", "a.b"), Helpers.mapping("=a", "c"));

      // when
      final var results = Helpers.resolve(mappings, Map.of("a", Map.of("z", 99)));

      // then: ordered's fallback readback merges the ancestor's untouched z in when c reads the
      // whole (partially-mapped) a; legacy's combined FEEL context literal already has its own
      // entry for a's mapped sub-key by this point, so the outer scope's z is no longer reachable
      // through it
      Helpers.assertDiffers(
          results, "{'a':{'b':1},'c':{'z':99,'b':1}}", "{'a':{'b':1},'c':{'b':1}}");
    }

    @Test
    @DisplayName("7.4 overriding one field, then reading the whole object -- diverges from 8.9.0")
    void shouldSeeAnAncestorFieldNotMappedWhenReadingTheWholeObject() {
      // given
      final var mappings = List.of(Helpers.mapping("=3", "x.a"), Helpers.mapping("=x", "y"));

      // when
      final var results = Helpers.resolve(mappings, Map.of("x", Map.of("a", 1, "b", 2)));

      // then: same mechanism as 7.3
      Helpers.assertDiffers(
          results, "{'x':{'a':3},'y':{'a':3,'b':2}}", "{'x':{'a':3},'y':{'a':3}}");
    }

    @Test
    @DisplayName(
        "7.5 two ancestor scopes -- the fall-through stops at the nearest, diverges from 8.9.0")
    void shouldResolveAgainstTheNearestAncestorScopeOnly() {
      // given: the mapped element sits inside a sub-process, so the chain is
      // element -> sub-process -> process instance
      final var mappings = List.of(Helpers.mapping("=3", "x.a"), Helpers.mapping("=x", "y"));
      final Map<String, Object> subProcessScope = Map.of("x", Map.of("a", 1, "b", 2));
      final Map<String, Object> processInstanceScope = Map.of("x", Map.of("a", 1, "b", 2, "c", 3));

      // when
      final var results =
          Helpers.resolveWithScopeChain(mappings, subProcessScope, processInstanceScope);

      // then: 'c' (only on the outer, process-instance scope) never reaches y under either
      // resolver -- the chain resolves to the nearest scope's x and stops there; only the
      // nearest scope's untouched 'b' leaks through, and only for ordered
      Helpers.assertDiffers(
          results, "{'x':{'a':3},'y':{'a':3,'b':2}}", "{'x':{'a':3},'y':{'a':3}}");
    }

    @Test
    @DisplayName(
        "7.6 partial shadowing when the variable lives in the element's own (nearest) scope"
            + " -- simulates multi-instance inputElement, diverges from 8.9.0")
    void shouldLayerOverAVariableInTheElementsOwnScope() {
      // given: 'item' is in the nearest scope, as a multi-instance inputElement variable would be;
      // mapping 1 partially shadows item by overriding item.a; mapping 2 reads the whole item back
      final var mappings = List.of(Helpers.mapping("=3", "item.a"), Helpers.mapping("=item", "z"));
      final Map<String, Object> elementScope = Map.of("item", Map.of("a", 1, "b", 2));

      // when
      final var results = Helpers.resolveWithScopeChain(mappings, elementScope, Map.of());

      // then: ordered's fallback readback merges the nearest scope's untouched 'b' into z;
      // combined's FEEL context is {item:{a:3},z:item} -- 'item' in 'z:item' resolves against
      // the context literal's own 'item' entry ({a:3}), so 'b' from the element scope is lost
      Helpers.assertDiffers(
          results, "{'item':{'a':3},'z':{'a':3,'b':2}}", "{'item':{'a':3},'z':{'a':3}}");
    }
  }

  @Nested
  @DisplayName(
      "Rule 8: types survive across input mappings within one resolver context, but not when"
          + " written through MsgPack (confirmed bug camunda/camunda#60011). OrderedMappingResolver"
          + " is the broken party here: it loses the FEEL type at every mapping boundary via the"
          + " MsgPack round-trip. CombinedMappingResolver evaluates all mappings in one FEEL"
          + " context expression, so the type survives. When input-comparison-mode=ORDERED is used"
          + " with the COMBINED default, any mapping that reads a FEEL-typed value set by an"
          + " earlier mapping WILL trigger comparison warnings -- that is expected and correct."
          + " These tests will need updating once #60011 is fixed.")
  class Rule8TypesSurviveAcrossMappingsNotVariables {

    @Test
    @DisplayName(
        "8.1 OrderedMappingResolver loses the FEEL type at the mapping boundary (bug #60011);"
            + " CombinedMappingResolver preserves it within the single FEEL context")
    void shouldLoseTheFeelTypeAcrossMappingsInOrderedButNotInCombined() {
      final var mappings =
          List.of(Helpers.mapping("=duration(\"P1DT2H\")", "x"), Helpers.mapping("=x.days", "y"));

      final var results = Helpers.resolve(mappings, Map.of());

      // ORDERED: x is stored as MsgPack string after mapping 1; x.days in mapping 2 sees a
      // plain string, not a duration → y=null. COMBINED: x stays a live FEEL duration inside
      // the single context expression → x.days=1.
      Helpers.assertDiffers(results, "{'x':'P1DT2H','y':null}", "{'x':'P1DT2H','y':1}");
    }

    @Test
    @DisplayName("8.2 across variables -- the half that already works")
    void shouldLoseTheFeelTypeOnceAValueIsAVariable() {
      // given
      final var mappings = List.of(Helpers.mapping("=d.days", "y"));

      // when
      final var results = Helpers.resolve(mappings, Map.of("d", "P1DT2H"));

      // then: d is a variable (JSON), so its duration-ness is already gone on every version
      Helpers.assertSame(results, "{'y':null}");
    }

    @Test
    @DisplayName("8.3 control: the same access inside one mapping never crosses a boundary")
    void shouldKeepTheFeelTypeWithinASingleMapping() {
      // given
      final var mappings = List.of(Helpers.mapping("=duration(\"P1DT2H\").days", "y"));

      // when
      final var results = Helpers.resolve(mappings, Map.of());

      // then
      Helpers.assertSame(results, "{'y':1}");
    }
  }

  @Nested
  @DisplayName("issue #60551 regression coverage (not one of the doc's numbered rules)")
  class Issue60551RegressionCoverage {

    @Test
    @DisplayName("case 1: qualified self-reference 'authentication.type' -- diverges from 8.9.0")
    void shouldResolveAQualifiedSelfReferenceOnlyUnderOrderedEvaluation() {
      // given: a *qualified* self-reference to the root mapping still being built
      // ('authentication.type' from within a mapping targeting 'authentication.final')
      final var mappings =
          List.of(
              Helpers.mapping("=\"gihub\"", "authentication.type"),
              Helpers.mapping("=authentication.type", "authentication.final"));

      // when
      final var results = Helpers.resolve(mappings, Map.of());

      // then: ordered's per-mapping result builder already exposes the first mapping's partial
      // 'authentication' object, so the qualified '.type' access sees it; legacy's single FEEL
      // context literal has not bound 'authentication' at all yet when this entry is evaluated
      Helpers.assertDiffers(
          results,
          "{'authentication':{'type':'gihub','final':'gihub'}}",
          "{'authentication':{'type':'gihub','final':null}}");
    }

    @Test
    @DisplayName("case 2: bare-name sibling reference 'type' -- diverges from 8.9.0")
    void shouldResolveABareNameSiblingReferenceOnlyUnderLegacyEvaluation() {
      // given: a *bare-name* sibling reference ('type', not 'authentication.type') -- the exact
      // pattern the GitHub Outbound Connector's authentication.token mapping depends on
      final var mappings =
          List.of(
              Helpers.mapping("=\"gihub\"", "authentication.type"),
              Helpers.mapping("=type", "authentication.final"));

      // when
      final var results = Helpers.resolve(mappings, Map.of());

      // then: legacy resolves it via FEEL's own context-literal sibling-scoping rule (an entry
      // may reference an earlier sibling bound in the *same* context literal by bare name);
      // ordered has no equivalent -- a bare name never resolves against an earlier *nested*
      // target's key, so it falls through to (absent) scope and lands on null
      Helpers.assertDiffers(
          results,
          "{'authentication':{'type':'gihub','final':null}}",
          "{'authentication':{'type':'gihub','final':'gihub'}}");
    }
  }

  @Nested
  @DisplayName("additional regression coverage not addressed by the rules doc")
  class AdditionalCoverageBeyondTheRulesDoc {

    @Test
    @DisplayName("static source stays a string literal, not the FEEL-inferred number")
    void shouldKeepAStaticSourceAsAStringLiteral() {
      // given: source "1" stays the string "1" rather than being type-inferred to the number 1,
      // byte-identical to the pre-baddd911435 combined expression (including the #16043
      // double-quote escaping)
      final var mappings = List.of(Helpers.mapping("1", "num"));

      // when
      final var results = Helpers.resolve(mappings, Map.of());

      // then
      Helpers.assertSame(results, "{'num':'1'}");
    }

    @Test
    @DisplayName(
        "non-numeric static source (no leading '=') is preserved as its string value"
            + " in both resolvers")
    void shouldPreserveNonNumericStaticSourceAsStringValue() {
      // given: source "hello" has no leading '=' so the transformer treats it as a static literal
      // and wraps it in FEEL double-quotes ("\"hello\""); it must never be mistaken for a
      // variable reference or FEEL expression
      final var mappings = List.of(Helpers.mapping("hello", "greeting"));

      // when
      final var results = Helpers.resolve(mappings, Map.of());

      // then: both resolvers evaluate the static FEEL string literal and write the plain string
      Helpers.assertSame(results, "{'greeting':'hello'}");
    }

    @Test
    @DisplayName("mapping with no source at all resolves to null")
    void shouldResolveAMappingWithoutASourceToNull() {
      // given
      final var mappings = List.of(Helpers.mappingWithoutSource("value"));

      // when
      final var results = Helpers.resolve(mappings, Map.of());

      // then
      Helpers.assertSame(results, "{'value':null}");
    }

    @Test
    @DisplayName("secret placeholder reference is preserved untouched by both resolvers")
    void shouldPreserveASecretPlaceholderReferenceUntouched() {
      // given: secret substitution happens later, at job activation -- at input-mapping
      // evaluation time the reference must survive untouched as its own placeholder text so that
      // later substitution still finds it
      final var mappings = List.of(Helpers.mapping("=camunda.secrets.token", "token"));

      // when
      final var results = Helpers.resolve(mappings, Map.of());

      // then
      Helpers.assertSame(results, "{'token':'camunda.secrets.token'}");
    }
  }

  private static final class Helpers {

    /**
     * Builds a raw {@link ZeebeMapping} test double the same way a {@code zeebe:input} element
     * would be modeled: {@code source} and {@code target} strings passed through unchanged; the
     * real {@link VariableMappingTransformer} handles any parsing/escaping (including the {@code
     * StaticExpression} double-quote escaping from issue #16043).
     */
    private static ZeebeMapping mapping(final String source, final String target) {
      return new TestZeebeMapping(source, target);
    }

    /**
     * Builds a raw {@link ZeebeMapping} test double for a {@code zeebe:input} element with no
     * source.
     */
    private static ZeebeMapping mappingWithoutSource(final String target) {
      return new TestZeebeMapping(null, target);
    }

    /**
     * Resolves {@code mappings} against an in-memory ancestor scope seeded with {@code
     * ancestorVariables}.
     */
    private static ResolverResults resolve(
        final List<ZeebeMapping> mappings, final Map<String, Object> ancestorVariables) {
      return resolveAt(mappings, buildProcessor(ancestorVariables));
    }

    /**
     * Resolves {@code mappings} against a two-level in-memory scope chain: {@code nearestScope} is
     * the innermost ancestor (e.g. a sub-process), {@code outerScope} is above it.
     */
    private static ResolverResults resolveWithScopeChain(
        final List<ZeebeMapping> mappings,
        final Map<String, Object> nearestScope,
        final Map<String, Object> outerScope) {
      return resolveAt(mappings, buildProcessor(nearestScope, outerScope));
    }

    private static ResolverResults resolveAt(
        final List<ZeebeMapping> mappings, final MappingExpressionProcessor processor) {
      final var inputMappings =
          new VariableMappingTransformer().transformInputMappings(mappings, EXPRESSION_LANGUAGE);
      return new ResolverResults(
          ORDERED.resolveInputMappings(inputMappings, processor),
          LEGACY.resolveInputMappings(inputMappings, processor));
    }

    /**
     * Builds a {@link ScopedExpressionProcessor} backed by an in-memory scope chain. {@code scopes}
     * is ordered from innermost (nearest ancestor) to outermost: {@link
     * ScopedEvaluationContext#getVariable} walks them in that order and returns the first match, or
     * {@code null} (absent) if the name appears in none.
     */
    @SafeVarargs
    private static MappingExpressionProcessor buildProcessor(final Map<String, Object>... scopes) {
      final List<Map<String, DirectBuffer>> encoded =
          Arrays.stream(scopes)
              .map(
                  scope ->
                      scope.entrySet().stream()
                          .collect(
                              Collectors.<Map.Entry<String, Object>, String, DirectBuffer>toMap(
                                  Map.Entry::getKey,
                                  e ->
                                      new UnsafeBuffer(
                                          MsgPackConverter.convertToMsgPack(e.getValue())))))
              .toList();
      final ScopedEvaluationContext context =
          name -> {
            for (final var scope : encoded) {
              final var value = scope.get(name);
              if (value != null) {
                return Either.left(value);
              }
            }
            return Either.left(null);
          };
      // scopeKey=-1 → ExpressionProcessor uses the context directly (no processScoped call),
      // which is correct: the in-memory context owns its own lookup and ignores the key
      return new MappingExpressionProcessor(
          new ExpressionProcessor(EXPRESSION_LANGUAGE, context, DEFAULT_TIMEOUT)
              .withSecretReferenceContext(),
          new MappingContext(BufferUtil.wrapString("test-element"), -1L, -1L, -1L, ""));
    }

    /** Asserts both resolvers gave the exact same result document. */
    private static void assertSame(final ResolverResults results, final String expected) {
      assertBothResolversGave(results, expected, expected);
    }

    /**
     * Asserts the two resolvers gave different result documents -- the whole point of this test
     * class. Fails fast if the two expected values are accidentally identical, since that scenario
     * belongs under {@link #assertSame} instead.
     */
    private static void assertDiffers(
        final ResolverResults results, final String expectedOrdered, final String expectedLegacy) {
      if (expectedOrdered.equals(expectedLegacy)) {
        throw new IllegalArgumentException(
            "expectedOrdered equals expectedLegacy -- use assertSame instead of assertDiffers");
      }
      assertBothResolversGave(results, expectedOrdered, expectedLegacy);
    }

    private static void assertBothResolversGave(
        final ResolverResults results, final String expectedOrdered, final String expectedLegacy) {
      assertThat(results.ordered()).isRight();
      MsgPackUtil.assertEquality(results.ordered().get(), expectedOrdered);
      assertThat(results.legacy()).isRight();
      MsgPackUtil.assertEquality(results.legacy().get(), expectedLegacy);
    }
  }

  private record ResolverResults(
      Either<Failure, DirectBuffer> ordered, Either<Failure, DirectBuffer> legacy) {}

  /**
   * Minimal {@link ZeebeMapping} test double: just the two getters, no BPMN-model/DOM machinery.
   * {@code source} may be {@code null}, matching the {@code zeebe:input} element case where no
   * source is set.
   */
  private record TestZeebeMapping(@Nullable String source, String target) implements ZeebeMapping {
    @Override
    public String getSource() {
      return source;
    }

    @Override
    public String getTarget() {
      return target;
    }
  }
}
