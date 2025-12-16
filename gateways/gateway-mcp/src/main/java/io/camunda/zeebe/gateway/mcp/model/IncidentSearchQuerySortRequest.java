package io.camunda.zeebe.gateway.mcp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Objects;

/** IncidentSearchQuerySortRequest */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IncidentSearchQuerySortRequest {

  private FieldEnum field;
  private SortOrderEnum order = SortOrderEnum.ASC;

  public IncidentSearchQuerySortRequest() {
    super();
  }

  /** Constructor with only required parameters */
  public IncidentSearchQuerySortRequest(final FieldEnum field) {
    this.field = field;
  }

  public IncidentSearchQuerySortRequest field(final FieldEnum field) {
    this.field = field;
    return this;
  }

  /**
   * The field to sort by.
   *
   * @return field
   */
  @NotNull
  @Schema(
      name = "field",
      description = "The field to sort by.",
      requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("field")
  public FieldEnum getField() {
    return field;
  }

  public void setField(final FieldEnum field) {
    this.field = field;
  }

  public IncidentSearchQuerySortRequest order(final SortOrderEnum order) {
    this.order = order;
    return this;
  }

  /**
   * Get order
   *
   * @return order
   */
  @Valid
  @Schema(name = "order", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("order")
  public SortOrderEnum getOrder() {
    return order;
  }

  public void setOrder(final SortOrderEnum order) {
    this.order = order;
  }

  @Override
  public int hashCode() {
    return Objects.hash(field, order);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final IncidentSearchQuerySortRequest incidentSearchQuerySortRequest =
        (IncidentSearchQuerySortRequest) o;
    return Objects.equals(field, incidentSearchQuerySortRequest.field)
        && Objects.equals(order, incidentSearchQuerySortRequest.order);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append("class IncidentSearchQuerySortRequest {\n");
    sb.append("    field: ").append(toIndentedString(field)).append("\n");
    sb.append("    order: ").append(toIndentedString(order)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces (except the first line).
   */
  private String toIndentedString(final Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }

  /** The field to sort by. */
  public enum FieldEnum {
    INCIDENT_KEY("incidentKey"),

    PROCESS_DEFINITION_KEY("processDefinitionKey"),

    PROCESS_DEFINITION_ID("processDefinitionId"),

    PROCESS_INSTANCE_KEY("processInstanceKey"),

    ERROR_TYPE("errorType"),

    ERROR_MESSAGE("errorMessage"),

    ELEMENT_ID("elementId"),

    ELEMENT_INSTANCE_KEY("elementInstanceKey"),

    CREATION_TIME("creationTime"),

    STATE("state"),

    JOB_KEY("jobKey"),

    TENANT_ID("tenantId");

    private final String value;

    FieldEnum(final String value) {
      this.value = value;
    }

    @JsonValue
    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }

    @JsonCreator
    public static FieldEnum fromValue(final String value) {
      for (final FieldEnum b : FieldEnum.values()) {
        if (b.value.equalsIgnoreCase(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }
}
