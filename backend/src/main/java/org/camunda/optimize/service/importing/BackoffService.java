package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.importing.job.schedule.ImportScheduleJob;
import org.camunda.optimize.service.importing.job.schedule.PageBasedImportScheduleJob;
import org.camunda.optimize.service.util.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Askar Akhmerov
 */
@Component
public class BackoffService {
  private final Logger logger = LoggerFactory.getLogger(BackoffService.class);
  protected long generalBackoffCounter = STARTING_BACKOFF;
  protected static final long STARTING_BACKOFF = 0;
  protected HashMap<String,Long> jobBackoffCounters = new HashMap<>();

  @Autowired
  protected ConfigurationService configurationService;

  public void backoffAndSleep() {
    try {
      if (this.generalBackoffCounter < configurationService.getMaximumBackoff()) {
        this.generalBackoffCounter = this.generalBackoffCounter + 1;
      }
      long millis = configurationService.getGeneralBackoff() * this.generalBackoffCounter;
      logger.debug("all jobs are backing off , sleeping for [{}] ms", millis);
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      logger.error("Interrupting backoff", e);
    }
  }

  public void resetBackoff(PageBasedImportScheduleJob toExecute) {
    this.setJobBackoff(toExecute.getImportService().getElasticsearchType(), STARTING_BACKOFF);
    this.generalBackoffCounter = STARTING_BACKOFF;
  }

  public void setJobBackoff (String job, Long backoff) {
    this.jobBackoffCounters.put(job,backoff);
  }

  public long calculateJobBackoff(int pagesPassed, ImportScheduleJob toExecute) {
    long result;
    Long jobBackoff = getBackoffCounter(toExecute.getImportService().getElasticsearchType());
    if (pagesPassed == 0) {
      if (jobBackoff < configurationService.getMaximumBackoff()) {
        result = jobBackoff + 1;
        this.setJobBackoff(toExecute.getImportService().getElasticsearchType(), result);
      } else {
        result = jobBackoff;
      }
    } else {
      result = STARTING_BACKOFF;
    }
    return result;
  }

  public Long getBackoffCounter(String elasticsearchType) {
    Long jobBackoff = this.jobBackoffCounters.get(elasticsearchType);
    if (jobBackoff == null) {
      jobBackoff = STARTING_BACKOFF;
      this.setJobBackoff(elasticsearchType, STARTING_BACKOFF);
    }
    return jobBackoff;
  }

  public long getGeneralBackoffCounter() {
    return generalBackoffCounter;
  }

  public void resetBackoffCounters() {
    for (Map.Entry e : this.jobBackoffCounters.entrySet()) {
      e.setValue(STARTING_BACKOFF);
    }
    this.generalBackoffCounter = STARTING_BACKOFF;
  }

}
