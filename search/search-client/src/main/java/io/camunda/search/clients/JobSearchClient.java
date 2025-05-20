package io.camunda.search.clients;

import io.camunda.search.entities.JobEntity;
import io.camunda.search.query.JobQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.SecurityContext;

public interface JobSearchClient {

  SearchQueryResult<JobEntity> searchJobs(JobQuery query);

  JobSearchClient withSecurityContext(SecurityContext securityContext);
}
