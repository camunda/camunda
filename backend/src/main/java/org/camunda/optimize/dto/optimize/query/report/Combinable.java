package org.camunda.optimize.dto.optimize.query.report;

/**
 * Is used to check if two single reports can be combined with each other
 * to a combined report.
 */
public interface Combinable {

  boolean isCombinable(Object o);
}
