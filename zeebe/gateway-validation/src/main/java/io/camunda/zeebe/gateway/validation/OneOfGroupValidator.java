/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.validation;

import io.camunda.zeebe.gateway.validation.model.BranchDescriptor;
import io.camunda.zeebe.gateway.validation.model.GroupDescriptor;
import io.camunda.zeebe.gateway.validation.model.ValidationErrorCode;
import io.camunda.zeebe.gateway.validation.runtime.OneOfGroupRegistry;
import io.camunda.zeebe.gateway.validation.runtime.RawTokenCarrier;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Skeleton validator; actual matching logic will be generated + wired later. */
public final class OneOfGroupValidator implements ConstraintValidator<OneOfGroup, Object> {
  private static final Logger LOG = LoggerFactory.getLogger(OneOfGroupValidator.class);
  private static final ThreadLocal<String> LAST_MATCHED_BRANCH = new ThreadLocal<>();
  // owner: validation - accessor methods below are intentionally public for observability & tests

  private String groupId;
  private boolean strictExtra;
  private boolean strictTokenKinds;
  private boolean captureRawTokens;
  private boolean failOnAmbiguous;

  @Override
  public void initialize(final OneOfGroup annotation) {
    groupId = annotation.value();
    strictExtra = annotation.strictExtra();
    strictTokenKinds = annotation.strictTokenKinds();
    captureRawTokens = annotation.captureRawTokens();
    failOnAmbiguous = annotation.failOnAmbiguous();
  }

  @Override
  public boolean isValid(final Object value, final ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }

    // Refresh (defensive) from runtime annotation on the value's class
    final OneOfGroup runtimeAnn = value.getClass().getAnnotation(OneOfGroup.class);
    if (runtimeAnn != null) {
      groupId = runtimeAnn.value();
      strictExtra = runtimeAnn.strictExtra();
      strictTokenKinds = runtimeAnn.strictTokenKinds();
      captureRawTokens = runtimeAnn.captureRawTokens();
      failOnAmbiguous = runtimeAnn.failOnAmbiguous();
    }

    final GroupDescriptor descriptor = OneOfGroupRegistry.INSTANCE.get(groupId);
    if (descriptor == null) {
      return violation(context, ValidationErrorCode.GROUP_NOT_FOUND, groupId);
    }

    LOG.debug(
        "Validating object of type {} against group {} (strictExtra={}, strictTokenKinds={}, captureRawTokens={}, failOnAmbiguous={})",
        value.getClass().getName(),
        groupId,
        strictExtra,
        strictTokenKinds,
        captureRawTokens,
        failOnAmbiguous);

    final java.util.Map<String, Object> data = asDataMap(value);
    if (data == null) {
      final java.util.Map<String, Object> reflected = new java.util.HashMap<>();
      reflectPojoFields(value, reflected);
      if (reflected.isEmpty()) {
        return true; // nothing to validate
      }
      final java.util.Map<String, String> tokenKinds = captureRawTokens ? extractTokenKinds(value) : java.util.Collections.emptyMap();
      return validateAgainstDescriptor(reflected, tokenKinds, descriptor, context);
    }
    final java.util.Map<String, String> tokenKinds = captureRawTokens ? extractTokenKinds(value) : java.util.Collections.emptyMap();
    return validateAgainstDescriptor(data, tokenKinds, descriptor, context);
  }

  /** Expose the last matched branch id for the current thread (request scope). */
  public static String getLastMatchedBranchId() { return LAST_MATCHED_BRANCH.get(); }
  public static void clearThreadLocal() { LAST_MATCHED_BRANCH.remove(); }

  private boolean validateAgainstDescriptor(
      final java.util.Map<String, Object> data,
      final java.util.Map<String, String> tokenKinds,
      final GroupDescriptor descriptor,
      final ConstraintValidatorContext context) {
    int bestSpecificity = -1;
    int bestMatchCount = 0;
    String bestBranchId = null;
    final java.util.List<String> branchFailureSummaries = new java.util.ArrayList<>();
    final java.util.List<java.util.Map<String, Object>> branchFailures = new java.util.ArrayList<>();

    for (final BranchDescriptor b : descriptor.branches()) {
      final java.util.List<String> reasons = new java.util.ArrayList<>();
      if (matchesBranch(data, tokenKinds, b, reasons)) {
        final int spec = b.specificity();
        if (spec > bestSpecificity) {
          bestSpecificity = spec;
          bestMatchCount = 1;
          bestBranchId = Integer.toString(b.id());
        } else if (spec == bestSpecificity) {
          bestMatchCount++;
        }
      } else {
        final java.util.Map<String, Object> bf = new java.util.LinkedHashMap<>();
        bf.put("branchId", b.id());
        bf.put("specificity", b.specificity());
        bf.put("reasons", reasons);
        branchFailures.add(bf);
        branchFailureSummaries.add(
            "branch=" + b.id() + "(" + b.specificity() + ") " + (reasons.isEmpty() ? "mismatch" : String.join(";", reasons)));
      }
    }

    if (bestSpecificity < 0) {
      final java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
      payload.put("groupId", groupId);
      payload.put("code", ValidationErrorCode.NO_MATCH.name());
      payload.put("summary", summarize(branchFailureSummaries));
      payload.put("branchFailures", branchFailures);
      return violation(context, ValidationErrorCode.NO_MATCH, toJson(payload));
    }
    if (bestMatchCount > 1 && failOnAmbiguous) {
      final java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
      payload.put("groupId", groupId);
      payload.put("code", ValidationErrorCode.AMBIGUOUS.name());
      payload.put("bestSpecificity", bestSpecificity);
      payload.put("branchFailures", branchFailures);
      return violation(context, ValidationErrorCode.AMBIGUOUS, toJson(payload));
    }
  // success: store matched branch id
  LAST_MATCHED_BRANCH.set(bestBranchId);
    return true;
  }

  private java.util.Map<String, Object> asDataMap(final Object value) {
    if (value instanceof java.util.Map<?, ?> m) {
      final java.util.Map<String, Object> out = new java.util.HashMap<>();
      for (var e : m.entrySet()) {
        if (e.getKey() instanceof String s) {
          out.put(s, e.getValue());
        }
      }
      return out;
    }
    try {
      final var f = value.getClass().getDeclaredField("value");
      f.setAccessible(true);
      final Object inner = f.get(value);
      if (inner instanceof java.util.Map<?, ?>) {
        return asDataMap(inner);
      }
    } catch (NoSuchFieldException | IllegalAccessException ignored) {
    }
    return null;
  }

  private void reflectPojoFields(final Object value, final java.util.Map<String, Object> target) {
    Class<?> c = value.getClass();
    while (c != null && c != Object.class) {
      for (final var f : c.getDeclaredFields()) {
        final int mod = f.getModifiers();
        if (java.lang.reflect.Modifier.isStatic(mod) || java.lang.reflect.Modifier.isTransient(mod)) {
          continue;
        }
        final String name = f.getName();
        if ("this$0".equals(name)) {
          continue; // synthetic reference
        }
        try {
          f.setAccessible(true);
          final Object v = f.get(value);
          if (v != null) {
            target.putIfAbsent(name, v);
          }
        } catch (IllegalAccessException ignored) {
        }
      }
      c = c.getSuperclass();
    }
  }

  private java.util.Map<String, String> extractTokenKinds(final Object value) {
    if (value instanceof RawTokenCarrier carrier) {
      final java.util.Map<String, String> tk = carrier.getTokenKinds();
      if (tk != null) {
        return tk;
      }
    }
    try {
      final var f = value.getClass().getDeclaredField("tokens");
      f.setAccessible(true);
      final Object tk = f.get(value);
      if (tk instanceof java.util.Map<?, ?> raw) {
        final java.util.Map<String, String> out = new java.util.HashMap<>();
        for (var e : raw.entrySet()) {
          if (e.getKey() instanceof String s && e.getValue() != null) {
            out.put(s, e.getValue().toString());
          }
        }
        return out;
      }
    } catch (NoSuchFieldException | IllegalAccessException ignored) {
    }
    return java.util.Collections.emptyMap();
  }

  private boolean matchesBranch(
      final java.util.Map<String, Object> map,
      final java.util.Map<String, String> tokenKinds,
      final BranchDescriptor b,
      final java.util.List<String> reasons) {
    for (final String r : b.required()) {
      if (!map.containsKey(r)) {
    reasons.add("missing " + ptr(r));
        return false;
      }
    }
    if (strictExtra) {
      final java.util.Set<String> allowed = new java.util.HashSet<>();
      java.util.Collections.addAll(allowed, b.required());
      java.util.Collections.addAll(allowed, b.optional());
      for (final String k : map.keySet()) {
        if (!allowed.contains(k)) {
      reasons.add("extra " + ptr(k));
          return false;
        }
      }
    }
    final String[] orderedProps = merge(b.required(), b.optional());
    final var enumMatrix = b.enumLiteralsPerProperty();
    for (int i = 0; i < enumMatrix.length && i < orderedProps.length; i++) {
      final var literals = enumMatrix[i];
      if (literals != null && literals.length > 0) {
        final String prop = orderedProps[i];
        if (map.containsKey(prop)) {
          final Object v = map.get(prop);
            if (v != null) {
              final String vs = v.toString();
              boolean ok = false;
              for (final var lit : literals) {
                if (lit.value().equals(vs)) {
                  ok = true;
                  break;
                }
              }
              if (!ok) {
        reasons.add("invalid-enum " + ptr(prop) + " value=" + vs);
                return false;
              }
            }
        }
      }
    }
    for (final var p : b.patterns()) {
      final Object val = map.get(p.property());
      if (val != null) {
        final String s = val.toString();
        if (!p.pattern().matcher(s).matches()) {
      reasons.add("pattern-mismatch " + ptr(p.property()));
          return false;
        }
      }
    }
    if (strictTokenKinds && !tokenKinds.isEmpty()) {
      // Evaluate required + optional present properties
      for (final String prop : merge(b.required(), b.optional())) {
        if (!map.containsKey(prop)) continue;
        final String tk = tokenKinds.get(prop);
        if (tk != null && !tokenKindAccepts(tk, map.get(prop))) {
          reasons.add("token-kind-mismatch " + ptr(prop) + " got=" + tk);
          return false;
        }
        // Basic nested enforcement: if value is a Map or List, traverse one level and compare captured kinds.
        final Object val = map.get(prop);
        if (val instanceof java.util.Map<?, ?> nestedMap) {
          for (var e : nestedMap.entrySet()) {
            if (e.getKey() instanceof String nk) {
              final Object nv = e.getValue();
              final String nestedKey = prop + "/" + nk.replace("~", "~0").replace("/", "~1");
              final String ntk = tokenKinds.get(nestedKey);
              if (ntk != null && !tokenKindAccepts(ntk, nv)) {
                reasons.add("token-kind-mismatch " + ptr(prop) + "/" + nk + " got=" + ntk);
                return false;
              }
            }
          }
        } else if (val instanceof java.util.List<?> list) {
          for (int i = 0; i < list.size(); i++) {
            final Object nv = list.get(i);
            final String nestedKey = prop + "/" + i;
            final String ntk = tokenKinds.get(nestedKey);
            if (ntk != null && !tokenKindAccepts(ntk, nv)) {
              reasons.add("token-kind-mismatch " + ptr(prop) + "/" + i + " got=" + ntk);
              return false;
            }
          }
        }
      }
    }
    return true;
  }

  private static String[] merge(final String[] a, final String[] b) {
    final String[] out = new String[a.length + b.length];
    System.arraycopy(a, 0, out, 0, a.length);
    System.arraycopy(b, 0, out, a.length, b.length);
    return out;
  }

  private boolean violation(
      final ConstraintValidatorContext ctx, final ValidationErrorCode code, final String detail) {
    ctx.disableDefaultConstraintViolation();
    ctx.buildConstraintViolationWithTemplate(code + ":" + detail).addConstraintViolation();
    return false;
  }

  private String toJson(final Object o) {
    try {
      // lightweight JSON without pulling full Jackson dependency into runtime path (already present but avoid coupling)
      if (o instanceof java.util.Map<?, ?> m) {
        final StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (var e : m.entrySet()) {
          if (!first) sb.append(',');
            first = false;
          sb.append('"').append(escape(e.getKey().toString())).append('"').append(':').append(toJson(e.getValue()));
        }
        sb.append('}');
        return sb.toString();
      } else if (o instanceof java.util.List<?> l) {
        final StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < l.size(); i++) {
          if (i > 0) sb.append(',');
          sb.append(toJson(l.get(i)));
        }
        sb.append(']');
        return sb.toString();
      } else if (o instanceof String s) {
        return '"' + escape(s) + '"';
      } else if (o == null) {
        return "null";
      } else if (o instanceof Number || o instanceof Boolean) {
        return o.toString();
      }
      return '"' + escape(o.toString()) + '"';
    } catch (Exception ex) {
      return "\"serialization_error\"";
    }
  }

  private String escape(final String s) {
    final StringBuilder sb = new StringBuilder(s.length() + 8);
    for (int i = 0; i < s.length(); i++) {
      final char c = s.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.toString();
  }

  private String summarize(final java.util.List<String> failures) {
    if (failures.isEmpty()) {
      return "";
    }
    if (failures.size() <= 3) {
      return String.join(" | ", failures);
    }
    return String.join(" | ", failures.subList(0, 3)) + " | +" + (failures.size() - 3) + " more";
  }

  private boolean tokenKindAccepts(final String tokenKind, final Object value) {
    if (value == null) {
      return true;
    }
    return switch (tokenKind) {
      case "STRING" -> value instanceof String;
      case "NUMBER" -> value instanceof Number;
      case "BOOLEAN" -> value instanceof Boolean;
      case "OBJECT" -> value instanceof java.util.Map;
      case "ARRAY" -> value instanceof java.util.List || value.getClass().isArray();
      case "NULL" -> value == null;
      default -> true; // tolerate unknown kinds
    };
  }

  private String ptr(final String property) {
    if (property == null || property.isEmpty()) {
      return "/"; // root or empty
    }
    // Escape ~ -> ~0 and / -> ~1 per RFC 6901
    final String escaped = property.replace("~", "~0").replace("/", "~1");
    return "/" + escaped;
  }

}
