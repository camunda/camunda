package io.camunda.operate.webapp.reader;

import io.camunda.webapps.schema.entities.JobEntity;
import java.util.Optional;

public interface JobReader {
  /** Returns the JobEntity for the given flowNodeInstanceId, or Optional.empty() if not found. */
  Optional<JobEntity> getJobByFlowNodeInstanceId(String flowNodeInstanceId);
}
