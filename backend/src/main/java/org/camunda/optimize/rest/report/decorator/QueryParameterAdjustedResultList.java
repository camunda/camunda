package org.camunda.optimize.rest.report.decorator;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;

import javax.ws.rs.core.MultivaluedMap;
import java.util.List;

public interface QueryParameterAdjustedResultList {

  List<ReportDefinitionDto> adjustList();

  MultivaluedMap<String, String> getQueryParameters();
}
