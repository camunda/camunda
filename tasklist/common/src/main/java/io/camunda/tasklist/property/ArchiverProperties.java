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
package io.camunda.tasklist.property;

public class ArchiverProperties {

  private static final int DEFAULT_ARCHIVER_THREADS_COUNT = 1;

  private boolean rolloverEnabled = true;

  private int threadsCount = DEFAULT_ARCHIVER_THREADS_COUNT;

  /**
   * This format will be used to create timed indices. It must correspond to rolloverInterval
   * parameter.
   */
  private String rolloverDateFormat = "yyyy-MM-dd";

  private String elsRolloverDateFormat = "date";

  /**
   * Interval description for "date histogram" aggregation, which is used to group finished
   * instances.
   *
   * @see <a
   *     href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-bucket-datehistogram-aggregation.html">Elasticsearch
   *     docs</a>
   */
  private String rolloverInterval;

  private int rolloverBatchSize = 100;

  private String waitPeriodBeforeArchiving = "1h";

  private boolean ilmEnabled = false; // default due to usage of curator

  private String ilmMinAgeForDeleteArchivedIndices = "30d";

  /**
   * In case archiver runs without delays, two subsequent runs may try to process the same process
   * entities (because of Elasticsearch refresh behaviour). In general, it's fine, but there are two
   * side effects: 1. We do the job, that is not needed anymore -> wasting CPU time 2. Metrics will
   * become incorrect, as it's not possible to distinguish such (duplicated) calls from normal ones.
   */
  private int delayBetweenRuns = 2000;

  public String getIlmMinAgeForDeleteArchivedIndices() {
    return ilmMinAgeForDeleteArchivedIndices;
  }

  public void setIlmMinAgeForDeleteArchivedIndices(String ilmMinAgeForDeleteArchivedIndices) {
    this.ilmMinAgeForDeleteArchivedIndices = ilmMinAgeForDeleteArchivedIndices;
  }

  public boolean isIlmEnabled() {
    return ilmEnabled;
  }

  public void setIlmEnabled(boolean ilmEnabled) {
    this.ilmEnabled = ilmEnabled;
  }

  public boolean isRolloverEnabled() {
    return rolloverEnabled;
  }

  public void setRolloverEnabled(boolean rolloverEnabled) {
    this.rolloverEnabled = rolloverEnabled;
  }

  public String getRolloverDateFormat() {
    return rolloverDateFormat;
  }

  public void setRolloverDateFormat(String rolloverDateFormat) {
    this.rolloverDateFormat = rolloverDateFormat;
  }

  public String getElsRolloverDateFormat() {
    return elsRolloverDateFormat;
  }

  public void setElsRolloverDateFormat(String elsRolloverDateFormat) {
    this.elsRolloverDateFormat = elsRolloverDateFormat;
  }

  public String getRolloverInterval() {
    if (rolloverInterval == null) {
      rolloverInterval =
          TasklistProperties.getDatabase().equals(TasklistProperties.ELASTIC_SEARCH) ? "1d" : "Day";
    }
    return rolloverInterval;
  }

  public void setRolloverInterval(String rolloverInterval) {
    this.rolloverInterval = rolloverInterval;
  }

  public int getRolloverBatchSize() {
    return rolloverBatchSize;
  }

  public void setRolloverBatchSize(int rolloverBatchSize) {
    this.rolloverBatchSize = rolloverBatchSize;
  }

  public int getThreadsCount() {
    return threadsCount;
  }

  public void setThreadsCount(int threadsCount) {
    this.threadsCount = threadsCount;
  }

  public String getWaitPeriodBeforeArchiving() {
    return waitPeriodBeforeArchiving;
  }

  public void setWaitPeriodBeforeArchiving(String waitPeriodBeforeArchiving) {
    this.waitPeriodBeforeArchiving = waitPeriodBeforeArchiving;
  }

  public String getArchivingTimepoint() {
    return "now-" + waitPeriodBeforeArchiving;
  }

  public int getDelayBetweenRuns() {
    return delayBetweenRuns;
  }

  public void setDelayBetweenRuns(int delayBetweenRuns) {
    this.delayBetweenRuns = delayBetweenRuns;
  }
}
