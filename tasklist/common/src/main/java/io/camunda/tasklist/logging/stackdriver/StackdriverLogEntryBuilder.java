/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.logging.stackdriver;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.apache.logging.log4j.core.time.Instant;
import org.apache.logging.log4j.util.ReadOnlyStringMap;

public final class StackdriverLogEntryBuilder {

  public static final String ERROR_REPORT_LOCATION_CONTEXT_KEY = "reportLocation";
  private final ServiceContext service;
  private final Map<String, Object> context;

  private io.camunda.tasklist.logging.stackdriver.SourceLocation sourceLocation;
  private Severity severity;
  private String message;
  private StackTraceElement traceElement;
  private String type;
  private String exception;
  private Instant time;

  StackdriverLogEntryBuilder() {
    service = new ServiceContext();
    context = new HashMap<>();
  }

  public StackdriverLogEntryBuilder withLevel(final Level level) {
    switch (level.getStandardLevel()) {
      case FATAL:
        severity = Severity.CRITICAL;
        break;
      case ERROR:
        severity = Severity.ERROR;
        break;
      case WARN:
        severity = Severity.WARNING;
        break;
      case INFO:
        severity = Severity.INFO;
        break;
      case DEBUG:
      case TRACE:
        severity = Severity.DEBUG;
        break;
      case OFF:
      case ALL:
      default:
        severity = Severity.DEFAULT;
        break;
    }

    return this;
  }

  public StackdriverLogEntryBuilder withSource(final StackTraceElement traceElement) {
    this.traceElement = traceElement;
    return this;
  }

  public StackdriverLogEntryBuilder withTime(final Instant time) {
    this.time = time;
    return this;
  }

  public StackdriverLogEntryBuilder withMessage(final String message) {
    this.message = message;
    return this;
  }

  public StackdriverLogEntryBuilder withServiceName(final String serviceName) {
    service.setService(serviceName);
    return this;
  }

  public StackdriverLogEntryBuilder withServiceVersion(final String serviceVersion) {
    service.setVersion(serviceVersion);
    return this;
  }

  public StackdriverLogEntryBuilder withContextEntry(final String key, final Object value) {
    context.put(key, value);
    return this;
  }

  public StackdriverLogEntryBuilder withDiagnosticContext(final ReadOnlyStringMap context) {
    context.forEach(this::withContextEntry);
    return this;
  }

  public StackdriverLogEntryBuilder withException(final ThrowableProxy error) {
    return withException(error.getExtendedStackTraceAsString());
  }

  public StackdriverLogEntryBuilder withType(final String type) {
    this.type = type;
    return this;
  }

  public StackdriverLogEntryBuilder withException(final String exception) {
    this.exception = exception;
    return this;
  }

  public StackdriverLogEntryBuilder withLogger(final String logger) {
    return withContextEntry("loggerName", logger);
  }

  public StackdriverLogEntryBuilder withThreadName(final String threadName) {
    return withContextEntry("threadName", threadName);
  }

  public StackdriverLogEntryBuilder withThreadId(final long threadId) {
    return withContextEntry("threadId", threadId);
  }

  public StackdriverLogEntryBuilder withThreadPriority(final int threadPriority) {
    return withContextEntry("threadPriority", threadPriority);
  }

  public StackdriverLogEntry build() {
    final StackdriverLogEntry stackdriverLogEntry = new StackdriverLogEntry();

    if (traceElement != null) {
      sourceLocation = mapStackTraceToSourceLocation(traceElement);

      if (severity == Severity.ERROR && exception == null) {
        context.putIfAbsent(
            ERROR_REPORT_LOCATION_CONTEXT_KEY, mapStackTraceToReportLocation(traceElement));
      }
    }

    if (severity == Severity.ERROR && type == null) {
      type = StackdriverLogEntry.ERROR_REPORT_TYPE;
    }

    if (time != null) {
      stackdriverLogEntry.setTimestampSeconds(time.getEpochSecond());
      stackdriverLogEntry.setTimestampNanos(time.getNanoOfSecond());
    }

    stackdriverLogEntry.setSeverity(severity.name());
    stackdriverLogEntry.setSourceLocation(sourceLocation);
    stackdriverLogEntry.setMessage(Objects.requireNonNull(message));
    stackdriverLogEntry.setService(service);
    stackdriverLogEntry.setContext(context);
    stackdriverLogEntry.setType(type);
    stackdriverLogEntry.setException(exception);

    return stackdriverLogEntry;
  }

  private SourceLocation mapStackTraceToSourceLocation(final StackTraceElement stackTrace) {
    final var location = new SourceLocation();
    location.setFile(stackTrace.getFileName());
    location.setFunction(stackTrace.getMethodName());
    location.setLine(stackTrace.getLineNumber());

    return location;
  }

  private ReportLocation mapStackTraceToReportLocation(final StackTraceElement stackTrace) {
    final var location = new ReportLocation();
    location.setFilePath(stackTrace.getFileName());
    location.setFunctionName(stackTrace.getMethodName());
    location.setLineNumber(stackTrace.getLineNumber());

    return location;
  }
}
