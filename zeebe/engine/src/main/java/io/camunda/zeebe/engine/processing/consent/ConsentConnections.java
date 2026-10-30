/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.consent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.engine.state.clustervariable.ClusterVariableInstance;
import io.camunda.zeebe.engine.state.immutable.ClusterVariableState;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads connections from cluster-variable state. A connection is the globally scoped cluster
 * variable {@code connection_<id>}, whose value is the connection document of the design.
 *
 * <p>The gate reads it from state by its id and never evaluates an expression in an instance's
 * scope, because a process variable shadows a cluster variable of the same name in FEEL: whoever
 * starts an instance could otherwise rewrite the rules that gate it.
 */
public final class ConsentConnections {

  public static final String VARIABLE_PREFIX = "connection_";
  private static final ObjectMapper JSON = new ObjectMapper();

  private final ClusterVariableState clusterVariableState;

  public ConsentConnections(final ClusterVariableState clusterVariableState) {
    this.clusterVariableState = clusterVariableState;
  }

  /** Returns the operation of the connection, or a message naming what does not exist. */
  public Either<String, Operation> findOperation(
      final String connectionId, final String operation) {
    final var connection = find(connectionId);
    if (connection.isEmpty()) {
      return Either.left(
          "Expected to find the connection '%s' as the cluster variable '%s%s', but none exists"
              .formatted(connectionId, VARIABLE_PREFIX, connectionId));
    }
    final var found = connection.get().operations().get(operation);
    return found == null
        ? Either.left(
            "Expected the connection '%s' to declare the operation '%s', but it declares only %s"
                .formatted(connectionId, operation, connection.get().operations().keySet()))
        : Either.right(found);
  }

  public Optional<Connection> find(final String connectionId) {
    return clusterVariableState
        .getGloballyScopedClusterVariable(BufferUtil.wrapString(VARIABLE_PREFIX + connectionId))
        .flatMap(ConsentConnections::parse);
  }

  /** Returns the connection whose credential is the secret with this name, if any. */
  public Optional<Connection> findByCredential(final String secretName) {
    final List<Connection> owners = new ArrayList<>();
    clusterVariableState.visitGloballyScopedClusterVariables(
        (name, instance) -> {
          if (name.startsWith(VARIABLE_PREFIX)) {
            parse(instance)
                .filter(connection -> secretName.equals(connection.credential()))
                .ifPresent(owners::add);
          }
          return owners.isEmpty();
        });
    return owners.stream().findFirst();
  }

  private static Optional<Connection> parse(final ClusterVariableInstance instance) {
    try {
      JsonNode document = JSON.readTree(MsgPackConverter.convertToJson(instance.getValueBuffer()));
      if (document.isTextual()) {
        document = JSON.readTree(document.asText());
      }
      final Map<String, Operation> operations = new LinkedHashMap<>();
      for (final JsonNode operation : document.path("operations")) {
        final var parsed =
            new Operation(
                operation.path("id").asText(),
                operation.path("label").asText(operation.path("id").asText()),
                "required".equals(operation.path("consent").asText("required")),
                Scope.of(operation.path("scope").asText("either")),
                texts(operation.path("relationship")),
                texts(operation.path("preconditions")),
                admitRules(operation.path("admit")));
        operations.put(parsed.id(), parsed);
      }
      return Optional.of(
          new Connection(
              document.path("id").asText(),
              document.path("name").asText(document.path("id").asText()),
              credentialName(document.path("credential").asText("")),
              operations));
    } catch (final Exception e) {
      return Optional.empty();
    }
  }

  private static String credentialName(final String credential) {
    final String prefix = "camunda.secrets.";
    return credential.startsWith(prefix) ? credential.substring(prefix.length()) : credential;
  }

  private static List<String> texts(final JsonNode array) {
    final List<String> texts = new ArrayList<>();
    array.forEach(node -> texts.add(node.asText()));
    return texts;
  }

  private static List<AdmitRule> admitRules(final JsonNode array) {
    final List<AdmitRule> rules = new ArrayList<>();
    for (final JsonNode rule : array) {
      if (rule.has("relation")) {
        rules.add(new AdmitRule(AdmitRule.Kind.RELATION, rule.path("relation").asText()));
      } else if (rule.has("client")) {
        rules.add(new AdmitRule(AdmitRule.Kind.CLIENT, rule.path("client").asText()));
      } else if (rule.has("process")) {
        rules.add(new AdmitRule(AdmitRule.Kind.PROCESS, rule.path("process").asText()));
      }
    }
    return rules;
  }

  /**
   * @param credential the name of the secret that holds the target system's service credential;
   *     only a job that passed the gate receives its value
   */
  public record Connection(
      String id, String name, String credential, Map<String, Operation> operations) {}

  /**
   * @param consentRequired whether the gate runs for this operation; an operator decision, so an
   *     existing model gains the gate by configuration
   * @param relationship paths that authorise a change without consent, checked by a worker
   * @param preconditions paths on the subject compared to a constant, checked by a worker
   * @param admit who besides the subject may ask under the subject's grant
   */
  public record Operation(
      String id,
      String label,
      boolean consentRequired,
      Scope scope,
      List<String> relationship,
      List<String> preconditions,
      List<AdmitRule> admit) {}

  /** What a subject may give for an operation: a one-time approval, a grant, or either. */
  public enum Scope {
    APPROVAL,
    EITHER,
    GRANT;

    static Scope of(final String value) {
      return switch (value.toLowerCase()) {
        case "approval" -> APPROVAL;
        case "grant" -> GRANT;
        default -> EITHER;
      };
    }

    public boolean allowsApproval() {
      return this != GRANT;
    }

    public boolean allowsGrant() {
      return this != APPROVAL;
    }
  }

  /**
   * A rule that admits a requester other than the subject under the subject's grant. A relational
   * rule needs data from the target system, so the gate cannot evaluate it: it admits the request
   * and hands the path to the worker, which denies when the path does not resolve to the requester.
   */
  public record AdmitRule(Kind kind, String value) {
    public enum Kind {
      RELATION,
      CLIENT,
      PROCESS
    }
  }
}
