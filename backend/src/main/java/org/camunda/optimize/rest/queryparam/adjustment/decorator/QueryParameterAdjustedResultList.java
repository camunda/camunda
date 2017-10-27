package org.camunda.optimize.rest.queryparam.adjustment.decorator;

import org.camunda.optimize.dto.optimize.query.util.SortableFields;

import javax.ws.rs.core.MultivaluedMap;
import java.util.List;

public interface QueryParameterAdjustedResultList {

  <T extends SortableFields> List<T> adjustList();

  MultivaluedMap<String, String> getQueryParameters();
}
