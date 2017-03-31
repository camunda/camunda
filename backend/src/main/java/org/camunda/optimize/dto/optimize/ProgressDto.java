package org.camunda.optimize.dto.optimize;

public class ProgressDto {

  protected int progress;

  /**
   * A number between 0 and 100 showing the progress.
   */
  public int getProgress() {
    return progress;
  }

  public void setProgress(int progress) {
    this.progress = progress;
  }
}
