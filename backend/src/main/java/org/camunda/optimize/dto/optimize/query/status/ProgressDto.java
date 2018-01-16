package org.camunda.optimize.dto.optimize.query.status;

public class ProgressDto {

  protected long progress;

  /**
   * A number between 0 and 100 showing the progress.
   */
  public long getProgress() {
    return progress;
  }

  public void setProgress(long progress) {
    this.progress = progress;
  }
}
