package io.camunda.client.api.search.request;

import io.camunda.client.api.search.filter.JobFilter;
import io.camunda.client.api.search.response.Job;
import io.camunda.client.api.search.sort.JobSort;

public interface JobSearchRequest
    extends TypedSearchRequest<JobFilter, JobSort, JobSearchRequest>, FinalSearchRequestStep<Job> {}
