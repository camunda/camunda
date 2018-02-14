package org.camunda.optimize.dto.optimize.query.sharing;

import org.camunda.optimize.dto.optimize.query.report.result.ReportResultDto;

/**
 * @author Askar Akhmerov
 */
public class EvaluatedReportShareDto <T extends ReportResultDto> extends ReportShareDto {

  private T report;

  public EvaluatedReportShareDto() {
    this(null);
  }

  public EvaluatedReportShareDto(ReportShareDto base) {
    if (base != null) {
      this.setId(base.getId());
      this.setType(base.getType());
      this.setReportId(base.getReportId());
    }
  }

  public void setReport(T report) {
    this.report = report;
  }

  public T getReport() {
    return report;
  }
}
