package org.camunda.optimize.service.es.report.result;

import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public abstract class ReportResult<ResultDto extends ReportDefinitionDto & ReportResultDto, Data extends ReportDataDto> {

  protected final ResultDto reportResultDto;

  public ReportResult(@NotNull ResultDto reportResultDto) {
    Objects.requireNonNull(reportResultDto, "The report result dto is not allowed to be null!");
    this.reportResultDto = reportResultDto;
  }

  public ResultDto getResultAsDto() {
    return reportResultDto;
  }

  public abstract List<String[]> getResultAsCsv(final Integer limit, final Integer offset, Set<String> excludedColumns);

  public abstract void copyReportData(Data data);

  public void copyMetaData(ReportDefinitionDto definitionDto) {
    reportResultDto.setId(definitionDto.getId());
    reportResultDto.setName(definitionDto.getName());
    reportResultDto.setOwner(definitionDto.getOwner());
    reportResultDto.setCreated(definitionDto.getCreated());
    reportResultDto.setLastModifier(definitionDto.getLastModifier());
    reportResultDto.setLastModified(definitionDto.getLastModified());
  }
}
