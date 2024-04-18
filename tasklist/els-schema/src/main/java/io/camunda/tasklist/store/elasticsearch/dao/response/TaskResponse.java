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
package io.camunda.tasklist.store.elasticsearch.dao.response;

import static java.util.Map.Entry.comparingByKey;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskResponse {

  @JsonProperty("completed")
  private boolean completed;

  @JsonProperty("task")
  private Task task;

  @JsonProperty("error")
  private Error error;

  @JsonProperty("response")
  private TaskResponseDetails responseDetails;

  public boolean isCompleted() {
    return completed;
  }

  public Task getTask() {
    return task;
  }

  @JsonIgnore
  public Status getTaskStatus() {
    return Optional.ofNullable(task).flatMap(Task::getStatus).orElse(null);
  }

  @JsonIgnore
  public Double getProgress() {
    return Optional.ofNullable(task)
        .flatMap(Task::getStatus)
        .filter(status -> status.getTotal() != 0)
        .map(
            status ->
                ((double) (status.getCreated() + status.getUpdated() + status.getDeleted()))
                    / status.getTotal())
        .orElse(0.0D);
  }

  public TaskResponseDetails getResponseDetails() {
    return responseDetails;
  }

  public Error getError() {
    return error;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Task {

    @JsonProperty("id")
    private String id;

    @JsonProperty("status")
    private Status status;

    public String getId() {
      return id;
    }

    @JsonIgnore
    public Optional<Status> getStatus() {
      return Optional.ofNullable(status);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Status {

    @JsonProperty("total")
    private Long total;

    @JsonProperty("updated")
    private Long updated;

    @JsonProperty("created")
    private Long created;

    @JsonProperty("deleted")
    private Long deleted;

    public Long getTotal() {
      return total;
    }

    public Long getUpdated() {
      return updated;
    }

    public Long getCreated() {
      return created;
    }

    public Long getDeleted() {
      return deleted;
    }
  }

  public static class Error {

    @JsonProperty("type")
    private String type;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("script_stack")
    private List<String> scriptStack;

    @JsonProperty("caused_by")
    private Map<String, Object> causedBy;

    public String getType() {
      return type;
    }

    public String getReason() {
      return reason;
    }

    public List<String> getScriptStack() {
      return scriptStack;
    }

    public Map<String, Object> getCausedBy() {
      return causedBy;
    }

    @Override
    public String toString() {
      final String scriptStackString =
          scriptStack == null
              ? null
              : scriptStack.stream()
                  .map(stackLine -> "\n" + stackLine)
                  .collect(Collectors.toList())
                  .toString();

      final String causedByString =
          Optional.ofNullable(causedBy)
              .map(
                  causes ->
                      causes.entrySet().stream()
                          .sorted(comparingByKey())
                          .map(entry -> entry.getKey() + "=" + entry.getValue())
                          .collect(Collectors.joining(",", "'{", "}'")))
              .orElse(null);

      return "Error{"
          + "type='"
          + type
          + "\', reason='"
          + reason
          + '\''
          + ", script_stack='"
          + scriptStackString
          + "\'\n"
          + "caused_by="
          + causedByString
          + '}';
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class TaskResponseDetails {

    @JsonProperty("failures")
    private List<Object> failures;

    public List<Object> getFailures() {
      return failures;
    }
  }
}
