/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate.property;

import java.util.UUID;

public class OperationExecutorProperties {

  public static final int BATCH_SIZE_DEFAULT = 500;
  public static final int DELETION_BATCH_SIZE_DEFAULT = 5000;

  public static final String WORKER_ID_DEFAULT = UUID.randomUUID().toString();

  public static final long LOCK_TIMEOUT_DEFAULT = 60000L; // 1 min

  private static final int DEFAULT_IMPORT_THREADS_COUNT = 3;

  private static final int DEFAULT_IMPORT_QUEUE_SIZE = 10;

  /**
   * Amount of process instances, that will be processed by one run of operation executor. This
   * counts process instances, but can end up in more operations, as one process instance can have
   * more than one scheduled operations.
   */
  private int batchSize = BATCH_SIZE_DEFAULT;

  private int deletionBatchSize = DELETION_BATCH_SIZE_DEFAULT;

  private String workerId = WORKER_ID_DEFAULT;

  /** Milliseconds. */
  private long lockTimeout = LOCK_TIMEOUT_DEFAULT;

  private boolean executorEnabled = true;

  private int threadsCount = DEFAULT_IMPORT_THREADS_COUNT;

  private int queueSize = DEFAULT_IMPORT_QUEUE_SIZE;

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  public int getDeletionBatchSize() {
    return deletionBatchSize;
  }

  public void setDeletionBatchSize(int deletionBatchSize) {
    this.deletionBatchSize = deletionBatchSize;
  }

  public String getWorkerId() {
    return workerId;
  }

  public void setWorkerId(String workerId) {
    this.workerId = workerId;
  }

  public long getLockTimeout() {
    return lockTimeout;
  }

  public void setLockTimeout(long lockTimeout) {
    this.lockTimeout = lockTimeout;
  }

  public boolean isExecutorEnabled() {
    return executorEnabled;
  }

  public void setExecutorEnabled(boolean executorEnabled) {
    this.executorEnabled = executorEnabled;
  }

  public int getThreadsCount() {
    return threadsCount;
  }

  public void setThreadsCount(int threadsCount) {
    this.threadsCount = threadsCount;
  }

  public int getQueueSize() {
    return queueSize;
  }

  public void setQueueSize(int queueSize) {
    this.queueSize = queueSize;
  }
}
