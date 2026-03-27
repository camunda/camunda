#!/usr/bin/env python3
"""Append strict filter overloads to SearchQueryFilterMapper.java"""
import sys, os

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAPPER_PATH = os.path.join(BASE, "src/main/java/io/camunda/gateway/mapping/http/search/SearchQueryFilterMapper.java")

# Read generated overloads
with open('/tmp/strict_filter_overloads.java') as f:
    overloads = f.read()

# Read current file
with open(MAPPER_PATH) as f:
    content = f.read()

# Manual toElementInstanceFilter overload
manual_element = """
  // Strict-contract overload for toElementInstanceFilter (hand-written — type field is String, not enum)
  static FlowNodeInstanceFilter toElementInstanceFilter(
      final GeneratedElementInstanceFilterStrictContract filter) {
    final var builder = FilterBuilders.flowNodeInstance();
    Optional.ofNullable(filter)
        .ifPresent(
            f -> {
              Optional.ofNullable(f.elementInstanceKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::flowNodeInstanceKeys);
              Optional.ofNullable(f.processInstanceKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::processInstanceKeys);
              Optional.ofNullable(f.processDefinitionKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::processDefinitionKeys);
              Optional.ofNullable(f.processDefinitionId())
                  .ifPresent(builder::processDefinitionIds);
              Optional.ofNullable(f.state())
                  .map(mapToOperations(String.class))
                  .ifPresent(builder::stateOperations);
              Optional.ofNullable(f.type())
                  .ifPresent(
                      t -> builder.types(FlowNodeType.fromZeebeBpmnElementType(t)));
              Optional.ofNullable(f.elementId()).ifPresent(builder::flowNodeIds);
              Optional.ofNullable(f.elementName()).ifPresent(builder::flowNodeNames);
              Optional.ofNullable(f.hasIncident()).ifPresent(builder::hasIncident);
              Optional.ofNullable(f.incidentKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::incidentKeys);
              Optional.ofNullable(f.tenantId()).ifPresent(builder::tenantIds);
              Optional.ofNullable(f.startDate())
                  .map(mapToOperations(OffsetDateTime.class))
                  .ifPresent(builder::startDateOperations);
              Optional.ofNullable(f.endDate())
                  .map(mapToOperations(OffsetDateTime.class))
                  .ifPresent(builder::endDateOperations);
              Optional.ofNullable(f.elementInstanceScopeKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::elementInstanceScopeKeys);
            });
    return builder.build();
  }
"""

# Variable value filter helpers for strict contract types
variable_helpers = """
  // Strict-contract overload for variable value filters
  private static Either<List<String>, List<VariableValueFilter>> toVariableValueFilters(
      final List<GeneratedVariableValueFilterPropertyStrictContract> filters) {
    if (CollectionUtils.isEmpty(filters)) {
      return Either.right(List.of());
    }

    final List<String> validationErrors = new ArrayList<>();
    final List<VariableValueFilter> variableValueFilters =
        filters.stream()
            .flatMap(
                filter -> {
                  if (filter.name() == null) {
                    validationErrors.add(ERROR_MESSAGE_NULL_VARIABLE_NAME);
                  }
                  if (filter.value() == null
                      || filter
                          .value()
                          .equals(SearchQueryRequestMapper.EMPTY_ADVANCED_STRING_FILTER)
                      || filter
                          .value()
                          .equals(SearchQueryRequestMapper.EMPTY_BASIC_STRING_FILTER)) {
                    validationErrors.add(ERROR_MESSAGE_NULL_VARIABLE_VALUE);
                  }
                  return validationErrors.isEmpty()
                      ? toVariableValueFiltersFromObject(filter.name(), filter.value()).stream()
                      : Stream.empty();
                })
            .toList();
    return validationErrors.isEmpty()
        ? Either.right(variableValueFilters)
        : Either.left(validationErrors);
  }

  private static List<VariableValueFilter> toVariableValueFiltersFromObject(
      final String name, final Object value) {
    final List<Operation<String>> operations = mapToOperations(String.class).apply(value);
    return new VariableValueFilter.Builder()
        .name(name)
        .valueTypedOperations(operations)
        .buildList();
  }
"""

# Find position of last }
idx = content.rfind('}')
new_content = (content[:idx]
    + '\n  // ===== Strict-contract filter overloads =====\n'
    + overloads
    + manual_element
    + variable_helpers
    + '}\n')

with open(MAPPER_PATH, 'w') as f:
    f.write(new_content)
print(f"Done. New line count: {new_content.count(chr(10))}")
