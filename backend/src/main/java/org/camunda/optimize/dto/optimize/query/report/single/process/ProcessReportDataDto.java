package org.camunda.optimize.dto.optimize.query.report.single.process;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.camunda.optimize.dto.optimize.query.report.Combinable;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.parameters.ParametersDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.parameters.ProcessPartDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.service.es.report.command.util.ReportUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

// TODO OPT-1612 ensures new empty parameters never win over legacy processPart being set
@JsonPropertyOrder({ "parameters", "processPart" })
public class ProcessReportDataDto extends SingleReportDataDto implements Combinable {

  protected String processDefinitionKey;
  protected String processDefinitionVersion;
  protected List<ProcessFilterDto> filter = new ArrayList<>();
  protected ProcessViewDto view;
  protected ProcessGroupByDto groupBy;
  protected ProcessVisualization visualization;
  protected ParametersDto parameters = new ParametersDto();

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public void setProcessDefinitionVersion(String processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }

  public List<ProcessFilterDto> getFilter() {
    return filter;
  }

  public void setFilter(List<ProcessFilterDto> filter) {
    this.filter = filter;
  }

  public ProcessViewDto getView() {
    return view;
  }

  public void setView(ProcessViewDto view) {
    this.view = view;
  }

  public ProcessGroupByDto getGroupBy() {
    return groupBy;
  }

  public void setGroupBy(ProcessGroupByDto groupBy) {
    this.groupBy = groupBy;
  }

  public ProcessVisualization getVisualization() {
    return visualization;
  }

  public void setVisualization(ProcessVisualization visualization) {
    this.visualization = visualization;
  }

  // TODO OPT-1612 just here for rest api backwardscompatibility
  @Deprecated
  public ProcessPartDto getProcessPart() {
    return Optional.ofNullable(getParameters())
      .flatMap(parameters -> Optional.ofNullable(parameters.getProcessPart()))
      .orElse(null);
  }

  // TODO OPT-1612 just here for rest api backwardscompatibility
  @Deprecated
  public void setProcessPart(ProcessPartDto processPart) {
    if (getParameters() != null) {
      getParameters().setProcessPart(processPart);
    } else {
      setParameters(new ParametersDto(processPart));
    }
  }

  public ParametersDto getParameters() {
    return parameters;
  }

  public void setParameters(ParametersDto parameters) {
    this.parameters = parameters;
  }

  @JsonIgnore
  public String createCommandKey() {
    String viewCommandKey = view == null ? "null" : view.createCommandKey();
    String groupByCommandKey = groupBy == null ? "null" : groupBy.createCommandKey();
    String processPartCommandKey = getProcessPart() == null ? "null" : getProcessPart().createCommandKey();
    return viewCommandKey + "_" + groupByCommandKey + "_" + processPartCommandKey;
  }

  @Override
  public boolean isCombinable(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ProcessReportDataDto)) {
      return false;
    }
    ProcessReportDataDto that = (ProcessReportDataDto) o;
    return ReportUtil.isCombinable(view, that.view) &&
      ReportUtil.isCombinable(groupBy, that.groupBy) &&
      Objects.equals(visualization, that.visualization);
  }

  @Override
  public String toString() {
    return "ProcessReportDataDto{" +
      "processDefinitionKey='" + processDefinitionKey + '\'' +
      ", processDefinitionVersion='" + processDefinitionVersion + '\'' +
      ", filter=" + filter +
      ", view=" + view +
      ", groupBy=" + groupBy +
      ", visualization='" + visualization + '\'' +
      ", parameters=" + parameters +
      '}';
  }
}
