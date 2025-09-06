/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.zeebe.gateway.validation.model.ValidationErrorCode;
import java.util.Map;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

public class RestErrorMapper {

  public static final Logger LOG = LoggerFactory.getLogger(RestErrorMapper.class);

  public static <T> Optional<ResponseEntity<T>> getResponse(final Throwable error) {
    return Optional.ofNullable(error)
        .map(RestErrorMapper::mapErrorToProblem)
        .map(RestErrorMapper::mapProblemToResponse);
  }

  public static ProblemDetail mapErrorToProblem(final Throwable error) {
    if (error == null) {
      return null;
    }
    // SeviceExceptions can be wrapped in Java exceptions because they are handled in Java futures
    if (error instanceof CompletionException || error instanceof ExecutionException) {
      return mapErrorToProblem(error.getCause());
    }
    if (error instanceof final ServiceException se) {
      final String msg = se.getMessage();
      final ProblemDetail base = createProblemDetail(mapStatus(se.getStatus()), msg, se.getStatus().name());
      // Attempt to parse validation detail of form CODE:{json}
      tryParseValidationPayload(msg, base);
      return base;
    } else {
      LOG.error("Expected to handle REST request, but an unexpected error occurred", error);
      return createProblemDetail(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Unexpected error occurred during the request processing: " + error.getMessage(),
          error.getClass().getName());
    }
  }

  private static void tryParseValidationPayload(final String message, final ProblemDetail problemDetail) {
    if (message == null) return;
    final int idx = message.indexOf(':');
    if (idx <= 0) return;
    final String prefix = message.substring(0, idx);
    if (!isValidationCode(prefix)) return;
    final String json = message.substring(idx + 1).trim();
    // Very small JSON parser for expected structure {"groupId":"...","code":"NO_MATCH",...}
    try {
      final Map<String, Object> map = MiniJson.parse(json);
      if (map != null) {
        problemDetail.setProperty("validationCode", prefix);
        for (var e : map.entrySet()) {
          problemDetail.setProperty(e.getKey(), e.getValue());
        }
        problemDetail.setDetail((String) map.getOrDefault("summary", problemDetail.getDetail()));
      }
    } catch (Exception ignored) {
      // fall back silently
    }
  }

  private static boolean isValidationCode(final String code) {
    for (final ValidationErrorCode c : ValidationErrorCode.values()) {
      if (c.name().equals(code)) return true;
    }
    return false;
  }

  // Minimal JSON parser (objects, arrays, strings, numbers, booleans, null) for constrained payload
  private static final class MiniJson {
    private final String s; private int i;
    private MiniJson(String s){this.s=s;}
    static Map<String,Object> parse(String s){return new MiniJson(s).parseObject();}
    private void ws(){while(i<s.length() && Character.isWhitespace(s.charAt(i))) i++;}
    private Map<String,Object> parseObject(){ws(); if(i>=s.length()||s.charAt(i)!='{') return null; i++; ws(); final java.util.Map<String,Object> m=new java.util.LinkedHashMap<>(); if(s.charAt(i)=='}'){i++; return m;} while(true){ws(); String k=parseString(); ws(); if(i>=s.length()||s.charAt(i)!=':') return m; i++; Object v=parseValue(); m.put(k,v); ws(); if(i>=s.length()) return m; char c=s.charAt(i++); if(c=='}') break; if(c!=',') break;} return m;}
    private java.util.List<Object> parseArray(){ws(); if(i>=s.length()||s.charAt(i)!='[') return java.util.List.of(); i++; ws(); final java.util.List<Object> a=new java.util.ArrayList<>(); if(s.charAt(i)==']'){i++; return a;} while(true){a.add(parseValue()); ws(); if(i>=s.length()) return a; char c=s.charAt(i++); if(c==']') break; if(c!=',') break;} return a;}
    private Object parseValue(){ws(); if(i>=s.length()) return null; char c=s.charAt(i); return switch(c){case '"'->parseString(); case '{'->parseObject(); case '['->parseArray(); case 't'->{if(s.startsWith("true",i)){i+=4;yield Boolean.TRUE;}yield null;} case 'f'->{if(s.startsWith("false",i)){i+=5;yield Boolean.FALSE;}yield null;} case 'n'->{if(s.startsWith("null",i)){i+=4;yield null;}yield null;} default -> parseNumber();};}
    private String parseString(){if(i>=s.length()||s.charAt(i)!='"') return ""; i++; final StringBuilder sb=new StringBuilder(); while(i<s.length()){char c=s.charAt(i++); if(c=='"') break; if(c=='\\'&& i<s.length()){char e=s.charAt(i++); sb.append(switch(e){case '"'->'"';case '\\'->'\\';case 'n'->'\n';case 'r'->'\r';case 't'->'\t';case 'u'->{String hex=s.substring(i,i+4); i+=4; yield (char)Integer.parseInt(hex,16);} default->e;});} else sb.append(c);} return sb.toString();}
    private Number parseNumber(){int start=i; while(i<s.length()){char c=s.charAt(i); if((c>='0'&&c<='9')||c=='-'||c=='+'||c=='.'||c=='e'||c=='E'){i++;} else break;} String num=s.substring(start,i); try{ if(num.contains(".")||num.contains("e")||num.contains("E")) return Double.parseDouble(num); return Long.parseLong(num);}catch(Exception e){return 0;} }
  }

  public static <T> ResponseEntity<T> mapErrorToResponse(@NotNull final Throwable error) {
    return mapProblemToResponse(mapErrorToProblem(error));
  }

  public static ProblemDetail createProblemDetail(
      final HttpStatusCode status, final String detail, final String title) {
    final var problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
    problemDetail.setTitle(title);
    return problemDetail;
  }

  public static <T> ResponseEntity<T> mapProblemToResponse(final ProblemDetail problemDetail) {
    return ResponseEntity.of(problemDetail)
        .headers(httpHeaders -> httpHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON))
        .build();
  }

  public static <T> CompletableFuture<ResponseEntity<T>> mapProblemToCompletedResponse(
      final ProblemDetail problemDetail) {
    return CompletableFuture.completedFuture(RestErrorMapper.mapProblemToResponse(problemDetail));
  }

  public static HttpStatus mapStatus(final Status status) {
    return switch (status) {
      case ABORTED -> HttpStatus.BAD_GATEWAY;
      case UNAVAILABLE, RESOURCE_EXHAUSTED -> HttpStatus.SERVICE_UNAVAILABLE;
      case UNKNOWN, INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
      case FORBIDDEN -> HttpStatus.FORBIDDEN;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
      case ALREADY_EXISTS, INVALID_STATE -> HttpStatus.CONFLICT;
      case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
      case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
    };
  }
}
