package org.camunda.optimize.rest.queryparam.adjustment.decorator;

import javax.ws.rs.core.MultivaluedMap;
import java.util.List;

public interface QueryParameterAdjustedResultList<T> {

  List<T> adjustList();

  MultivaluedMap<String, String> getQueryParameters();
}
