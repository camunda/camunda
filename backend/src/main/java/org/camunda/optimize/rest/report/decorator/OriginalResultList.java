package org.camunda.optimize.rest.report.decorator;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;

import javax.ws.rs.core.MultivaluedMap;
import java.util.List;

public class OriginalResultList implements QueryParameterAdjustedResultList {

  private List<ReportDefinitionDto> resultList;
  private MultivaluedMap<String, String> multivaluedMap;

  public OriginalResultList(List<ReportDefinitionDto> resultList, MultivaluedMap<String, String> multivaluedMap) {
    this.resultList = resultList;
    this.multivaluedMap = multivaluedMap;
  }

  @Override
  public List<ReportDefinitionDto> adjustList() {
    return resultList;
  }

  @Override
  public MultivaluedMap<String, String> getQueryParameters() {
    return multivaluedMap;
  }
}
