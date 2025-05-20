package io.camunda.search.clients.transformers.sort;

import static io.camunda.webapps.schema.descriptors.template.JobTemplate.FLOW_NODE_ID;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.FLOW_NODE_INSTANCE_ID;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.JOB_KEY;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.JOB_KIND;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.JOB_STATE;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.JOB_TYPE;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.JOB_WORKER;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.LISTENER_EVENT_TYPE;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.PROCESS_DEFINITION_KEY;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.TIME;

public class JobFieldSortingTransformer implements FieldSortingTransformer {

  @Override
  public String apply(final String domainField) {
    return switch (domainField) {
      case "processDefinitionKey" -> PROCESS_DEFINITION_KEY;
      case "processInstanceKey" -> PROCESS_INSTANCE_KEY;
      case "elementInstanceKey" -> FLOW_NODE_INSTANCE_ID;
      case "elementId" -> FLOW_NODE_ID;
      case "jobKey" -> JOB_KEY;
      case "jobType" -> JOB_TYPE;
      case "worker" -> JOB_WORKER;
      case "state" -> JOB_STATE;
      case "jobKind" -> JOB_KIND;
      case "listenerEventType" -> LISTENER_EVENT_TYPE;
      case "endDate" -> TIME;
      case "tenantId" -> TENANT_ID;
      default -> throw new IllegalArgumentException("Unknown field: " + domainField);
    };
  }
}
